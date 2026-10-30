package com.motoboutique.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.motoboutique.catalogo.domain.Categoria;
import com.motoboutique.catalogo.domain.Producto;
import com.motoboutique.catalogo.repository.ProductoRepository;
import com.motoboutique.inventario.domain.Almacen;
import com.motoboutique.inventario.domain.MovimientoInventario;
import com.motoboutique.inventario.domain.Stock;
import com.motoboutique.inventario.dto.MovimientoSolicitudDto;
import com.motoboutique.inventario.domain.MotivoSalida;
import com.motoboutique.inventario.repository.AlmacenRepository;
import com.motoboutique.inventario.repository.MovimientoInventarioRepository;
import com.motoboutique.inventario.repository.NumeracionDocumentoRepository;
import com.motoboutique.inventario.repository.StockRepository;
import com.motoboutique.inventario.repository.TransferenciaDetalleRepository;
import com.motoboutique.inventario.repository.TransferenciaRepository;
import com.motoboutique.shared.exception.NegocioException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MovimientoInventarioServiceTest {
    @Mock private ProductoRepository productos;
    @Mock private AlmacenRepository almacenes;
    @Mock private StockRepository stocks;
    @Mock private MovimientoInventarioRepository movimientos;
    @Mock private TransferenciaRepository transferencias;
    @Mock private TransferenciaDetalleRepository detalles;
    @Mock private NumeracionDocumentoRepository numeracion;
    @InjectMocks private MovimientoInventarioService servicio;

    private Producto producto;
    private Almacen almacen;
    private Stock stock;

    @BeforeEach
    void preparar() {
        Categoria categoria = new Categoria();
        producto = new Producto();
        ReflectionTestUtils.setField(producto, "id", 1L);
        producto.setCodigo("P-1");
        producto.setNombre("Producto");
        producto.setCategoria(categoria);
        producto.setPrecioCompra(new BigDecimal("10.00"));
        almacen = new Almacen();
        ReflectionTestUtils.setField(almacen, "id", 1L);
        almacen.setNombre("Principal");
        stock = new Stock();
        stock.setProducto(producto);
        stock.setAlmacen(almacen);
        stock.setCantidad(new BigDecimal("3.000"));
        stock.setStockReservado(BigDecimal.ZERO.setScale(3));
        when(productos.bloquearActivo(1L)).thenReturn(Optional.of(producto));
        when(almacenes.findByIdAndEliminadoFalse(1L)).thenReturn(Optional.of(almacen));
        when(stocks.bloquearPorProductoYAlmacen(1L, 1L)).thenReturn(Optional.of(stock));
        when(movimientos.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void calculaCostoPromedioPonderadoEnLaEntrada() {
        when(movimientos.findFirstByProductoIdAndAlmacenIdAndEliminadoFalseOrderByFechaDescIdDesc(1L, 1L))
                .thenReturn(Optional.empty());
        when(numeracion.siguienteNumero("ENT")).thenReturn("ENT-000001");

        servicio.registrarEntrada(new MovimientoSolicitudDto(1L, 1L,
                new BigDecimal("2.000"), new BigDecimal("16.00"), "OC-1", null, null));

        org.mockito.ArgumentCaptor<MovimientoInventario> captor =
                org.mockito.ArgumentCaptor.forClass(MovimientoInventario.class);
        org.mockito.Mockito.verify(movimientos).saveAndFlush(captor.capture());
        assertEquals(new BigDecimal("12.40"), captor.getValue().getCostoPromedio());
        assertEquals(new BigDecimal("5.000"), captor.getValue().getSaldoCantidad());
    }

    @Test
    void salidasConcurrentesNoPermitenStockNegativo() throws Exception {
        stock.setCantidad(new BigDecimal("5.000"));
        ReentrantLock bloqueoProducto = new ReentrantLock();
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger numero = new AtomicInteger();
        when(productos.bloquearActivo(1L)).thenAnswer(invocacion -> {
            bloqueoProducto.lock();
            return Optional.of(producto);
        });
        when(numeracion.siguienteNumero("SAL"))
                .thenAnswer(invocacion -> "SAL-" + numero.incrementAndGet());
        when(movimientos.findFirstByProductoIdAndAlmacenIdAndEliminadoFalseOrderByFechaDescIdDesc(1L, 1L))
                .thenReturn(Optional.empty());
        when(movimientos.saveAndFlush(any())).thenAnswer(invocacion -> {
            MovimientoInventario movimiento = invocacion.getArgument(0);
            stock.setCantidad(movimiento.getSaldoCantidad());
            return movimiento;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> primera = executor.submit(() -> salidaConcurrente(inicio, bloqueoProducto));
            Future<Boolean> segunda = executor.submit(() -> salidaConcurrente(inicio, bloqueoProducto));
            inicio.countDown();
            assertEquals(1, (primera.get() ? 1 : 0) + (segunda.get() ? 1 : 0));
            assertEquals(new BigDecimal("1.000"), stock.getCantidad());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean salidaConcurrente(CountDownLatch inicio, ReentrantLock bloqueoProducto) {
        try {
            inicio.await();
            servicio.registrarSalida(new MovimientoSolicitudDto(1L, 1L,
                    new BigDecimal("4.000"), null, null, MotivoSalida.MERMA, null));
            return true;
        } catch (NegocioException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            if (bloqueoProducto.isHeldByCurrentThread()) {
                bloqueoProducto.unlock();
            }
        }
    }
}
