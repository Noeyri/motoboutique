package com.motoboutique.inventario.service;

import com.motoboutique.catalogo.domain.Producto;
import com.motoboutique.catalogo.repository.ProductoRepository;
import com.motoboutique.inventario.domain.Almacen;
import com.motoboutique.inventario.domain.MovimientoInventario;
import com.motoboutique.inventario.domain.TipoMovimiento;
import com.motoboutique.inventario.domain.Transferencia;
import com.motoboutique.inventario.domain.TransferenciaDetalle;
import com.motoboutique.inventario.domain.Stock;
import com.motoboutique.inventario.dto.AjusteInventarioDto;
import com.motoboutique.inventario.dto.MovimientoDto;
import com.motoboutique.inventario.dto.MovimientoSolicitudDto;
import com.motoboutique.inventario.dto.TransferenciaDetalleSolicitudDto;
import com.motoboutique.inventario.dto.TransferenciaDto;
import com.motoboutique.inventario.dto.TransferenciaSolicitudDto;
import com.motoboutique.inventario.repository.AlmacenRepository;
import com.motoboutique.inventario.repository.MovimientoInventarioRepository;
import com.motoboutique.inventario.repository.NumeracionDocumentoRepository;
import com.motoboutique.inventario.repository.StockRepository;
import com.motoboutique.inventario.repository.TransferenciaDetalleRepository;
import com.motoboutique.inventario.repository.TransferenciaRepository;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovimientoInventarioService {
    private static final BigDecimal CANTIDAD_CERO = BigDecimal.ZERO.setScale(3);
    private static final BigDecimal COSTO_CERO = BigDecimal.ZERO.setScale(2);

    private final ProductoRepository productoRepository;
    private final AlmacenRepository almacenRepository;
    private final StockRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final TransferenciaDetalleRepository detalleRepository;
    private final NumeracionDocumentoRepository numeracionRepository;

    @Transactional
    public MovimientoDto registrarEntrada(MovimientoSolicitudDto solicitud) {
        validarCantidad(solicitud.cantidad());
        if (solicitud.costoUnitario() == null || solicitud.costoUnitario().signum() < 0) {
            throw new NegocioException("Indica un costo unitario válido para la entrada.");
        }
        Stock stock = stockBloqueado(solicitud.productoId(), solicitud.almacenId());
        BigDecimal costoVigente = costoPromedio(stock, solicitud.productoId(), solicitud.almacenId());
        BigDecimal costoEntrada = escalaCosto(solicitud.costoUnitario());
        BigDecimal nuevoCosto = stock.getCantidad().multiply(costoVigente)
                .add(solicitud.cantidad().multiply(costoEntrada))
                .divide(stock.getCantidad().add(solicitud.cantidad()), 2, RoundingMode.HALF_UP);
        return registrar(stock, TipoMovimiento.ENTRADA, solicitud.cantidad(), costoEntrada,
                nuevoCosto, numeracionRepository.siguienteNumero("ENT"),
                solicitud.documentoReferencia(), solicitud.observaciones());
    }

    @Transactional
    public MovimientoDto registrarRecepcion(Long productoId, Long almacenId, BigDecimal cantidad,
                                           BigDecimal costoUnitario, String numeroRecepcion,
                                           String numeroOrden) {
        validarCantidad(cantidad);
        if (costoUnitario == null || costoUnitario.signum() < 0) {
            throw new NegocioException("El costo unitario de la recepción no es válido.");
        }
        Stock stock = stockBloqueado(productoId, almacenId);
        BigDecimal costoVigente = costoPromedio(stock, productoId, almacenId);
        BigDecimal costoEntrada = escalaCosto(costoUnitario);
        BigDecimal nuevoCosto = stock.getCantidad().signum() == 0 ? costoEntrada
                : stock.getCantidad().multiply(costoVigente)
                        .add(cantidad.multiply(costoEntrada))
                        .divide(stock.getCantidad().add(cantidad), 2, RoundingMode.HALF_UP);
        return registrar(stock, TipoMovimiento.RECEPCION, cantidad, costoEntrada,
                nuevoCosto, numeroRecepcion, numeroOrden, "Recepción de orden de compra");
    }

    @Transactional
    public MovimientoDto registrarSalida(MovimientoSolicitudDto solicitud) {
        validarCantidad(solicitud.cantidad());
        if (solicitud.motivo() == null) {
            throw new NegocioException("Selecciona el motivo de la salida.");
        }
        Stock stock = stockBloqueado(solicitud.productoId(), solicitud.almacenId());
        validarDisponible(stock, solicitud.cantidad());
        BigDecimal promedio = costoPromedio(stock, solicitud.productoId(), solicitud.almacenId());
        return registrar(stock, TipoMovimiento.SALIDA, solicitud.cantidad(), promedio, promedio,
                numeracionRepository.siguienteNumero("SAL"), solicitud.documentoReferencia(),
                motivoSalida(solicitud));
    }

    @Transactional
    public MovimientoDto ajustar(AjusteInventarioDto solicitud) {
        if (solicitud.cantidadConSigno() == null || solicitud.cantidadConSigno().signum() == 0) {
            throw new NegocioException("La cantidad del ajuste no puede ser cero.");
        }
        BigDecimal cantidad = solicitud.cantidadConSigno().abs();
        validarCantidad(cantidad);
        Stock stock = stockBloqueado(solicitud.productoId(), solicitud.almacenId());
        BigDecimal promedio = costoPromedio(stock, solicitud.productoId(), solicitud.almacenId());
        TipoMovimiento tipo;
        BigDecimal costo;
        BigDecimal nuevoCosto = promedio;
        if (solicitud.cantidadConSigno().signum() > 0) {
            tipo = TipoMovimiento.AJUSTE_POSITIVO;
            if (solicitud.costoUnitario() == null) {
                throw new NegocioException("Indica el costo unitario para un ajuste positivo.");
            }
            costo = escalaCosto(solicitud.costoUnitario());
            nuevoCosto = stock.getCantidad().signum() == 0 ? costo
                    : stock.getCantidad().multiply(promedio).add(cantidad.multiply(costo))
                            .divide(stock.getCantidad().add(cantidad), 2, RoundingMode.HALF_UP);
        } else {
            tipo = TipoMovimiento.AJUSTE_NEGATIVO;
            validarDisponible(stock, cantidad);
            costo = promedio;
        }
        return registrar(stock, tipo, cantidad, costo, nuevoCosto,
                numeracionRepository.siguienteNumero("AJU"), solicitud.documentoReferencia(),
                solicitud.motivo());
    }

    @Transactional
    public TransferenciaDto crearTransferencia(TransferenciaSolicitudDto solicitud) {
        if (solicitud.almacenOrigenId().equals(solicitud.almacenDestinoId())) {
            throw new NegocioException("El almacén de origen debe ser distinto al de destino.");
        }
        Almacen origen = almacenActivo(solicitud.almacenOrigenId());
        Almacen destino = almacenActivo(solicitud.almacenDestinoId());
        Set<Long> productos = new HashSet<>();
        for (TransferenciaDetalleSolicitudDto linea : solicitud.detalles()) {
            validarCantidad(linea.cantidad());
            if (!productos.add(linea.productoId())) {
                throw new NegocioException("No repitas un producto dentro de la transferencia.");
            }
            productoActivo(linea.productoId());
        }
        Transferencia transferencia = new Transferencia();
        transferencia.setNumero(numeracionRepository.siguienteNumero("TRF"));
        transferencia.setAlmacenOrigen(origen);
        transferencia.setAlmacenDestino(destino);
        transferencia.setObservaciones(solicitud.observaciones());
        transferencia = transferenciaRepository.saveAndFlush(transferencia);
        for (TransferenciaDetalleSolicitudDto linea : solicitud.detalles()) {
            TransferenciaDetalle detalle = new TransferenciaDetalle();
            detalle.setTransferencia(transferencia);
            detalle.setProducto(productoActivo(linea.productoId()));
            detalle.setCantidad(linea.cantidad());
            detalleRepository.save(detalle);
        }
        detalleRepository.flush();
        return transferenciaDto(transferencia);
    }

    @Transactional
    public TransferenciaDto despachar(Long transferenciaId) {
        Transferencia transferencia = transferenciaBloqueada(transferenciaId);
        exigirEstado(transferencia, "PENDIENTE");
        List<TransferenciaDetalle> detalles = detallesOrdenados(transferenciaId);
        for (TransferenciaDetalle detalle : detalles) {
            Stock stock = stockBloqueado(detalle.getProducto().getId(),
                    transferencia.getAlmacenOrigen().getId());
            validarDisponible(stock, detalle.getCantidad());
            BigDecimal promedio = costoPromedio(stock, detalle.getProducto().getId(),
                    transferencia.getAlmacenOrigen().getId());
            MovimientoDto movimientoDto = registrar(stock, TipoMovimiento.TRANSFERENCIA_SALIDA,
                    detalle.getCantidad(), promedio, promedio, transferencia.getNumero(),
                    null, "Despacho de transferencia");
            detalle.setMovimientoSalida(movimientoRepository.getReferenceById(movimientoDto.id()));
        }
        transferencia.setEstado("EN_TRANSITO");
        detalleRepository.flush();
        return transferenciaDto(transferencia);
    }

    @Transactional
    public TransferenciaDto recibir(Long transferenciaId) {
        Transferencia transferencia = transferenciaBloqueada(transferenciaId);
        exigirEstado(transferencia, "EN_TRANSITO");
        for (TransferenciaDetalle detalle : detallesOrdenados(transferenciaId)) {
            BigDecimal costo = detalle.getMovimientoSalida().getCostoUnitario();
            Stock destino = stockBloqueado(detalle.getProducto().getId(),
                    transferencia.getAlmacenDestino().getId());
            BigDecimal promedio = costoPromedio(destino, detalle.getProducto().getId(),
                    transferencia.getAlmacenDestino().getId());
            BigDecimal promedioNuevo = destino.getCantidad().multiply(promedio)
                    .add(detalle.getCantidad().multiply(costo))
                    .divide(destino.getCantidad().add(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            MovimientoDto movimientoDto = registrar(destino, TipoMovimiento.TRANSFERENCIA_ENTRADA,
                    detalle.getCantidad(), costo, promedioNuevo, transferencia.getNumero(),
                    null, "Recepción de transferencia");
            detalle.setMovimientoEntrada(movimientoRepository.getReferenceById(movimientoDto.id()));
        }
        transferencia.setEstado("COMPLETADA");
        detalleRepository.flush();
        return transferenciaDto(transferencia);
    }

    @Transactional
    public TransferenciaDto cancelar(Long transferenciaId) {
        Transferencia transferencia = transferenciaBloqueada(transferenciaId);
        exigirEstado(transferencia, "PENDIENTE");
        transferencia.setEstado("CANCELADA");
        return transferenciaDto(transferencia);
    }

    private MovimientoDto registrar(Stock stock, TipoMovimiento tipo, BigDecimal cantidad,
                                   BigDecimal costoUnitario, BigDecimal costoPromedio,
                                   String numero, String documentoReferencia, String motivo) {
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProducto(stock.getProducto());
        movimiento.setAlmacen(stock.getAlmacen());
        movimiento.setTipo(tipo.name());
        movimiento.setCantidad(cantidad);
        movimiento.setCostoUnitario(escalaCosto(costoUnitario));
        movimiento.setCostoPromedio(escalaCosto(costoPromedio));
        movimiento.setSaldoCantidad(tipo.esEntrada()
                ? stock.getCantidad().add(cantidad) : stock.getCantidad().subtract(cantidad));
        movimiento.setReferencia(numero);
        movimiento.setDocumentoReferencia(documentoReferencia);
        movimiento.setMotivo(motivo);
        return movimientoDto(movimientoRepository.saveAndFlush(movimiento));
    }

    private Stock stockBloqueado(Long productoId, Long almacenId) {
        Producto producto = productoActivo(productoId);
        Almacen almacen = almacenActivo(almacenId);
        Stock stock = stockRepository.bloquearPorProductoYAlmacen(productoId, almacenId)
                .orElseGet(() -> {
                    Stock nuevo = new Stock();
                    nuevo.setProducto(producto);
                    nuevo.setAlmacen(almacen);
                    nuevo.setCantidad(CANTIDAD_CERO);
                    nuevo.setStockReservado(CANTIDAD_CERO);
                    return stockRepository.saveAndFlush(nuevo);
                });
        return stock;
    }

    private Producto productoActivo(Long id) {
        return productoRepository.bloquearActivo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró un producto activo con id " + id));
    }

    private Almacen almacenActivo(Long id) {
        Almacen almacen = almacenRepository.findByIdAndEliminadoFalse(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Almacén", id));
        if (!almacen.isActivo()) {
            throw new NegocioException("El almacén seleccionado está inactivo.");
        }
        return almacen;
    }

    private Transferencia transferenciaBloqueada(Long id) {
        return transferenciaRepository.bloquearActiva(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Transferencia", id));
    }

    private List<TransferenciaDetalle> detallesOrdenados(Long id) {
        List<TransferenciaDetalle> detalles =
                detalleRepository.findByTransferenciaIdAndEliminadoFalseOrderById(id);
        detalles.sort(Comparator.comparing(detalle -> detalle.getProducto().getId()));
        if (detalles.isEmpty()) {
            throw new NegocioException("La transferencia no tiene productos.");
        }
        return detalles;
    }

    private TransferenciaDto transferenciaDto(Transferencia transferencia) {
        List<TransferenciaDto.Detalle> detalles = detalleRepository
                .findByTransferenciaIdAndEliminadoFalseOrderById(transferencia.getId()).stream()
                .map(detalle -> new TransferenciaDto.Detalle(detalle.getProducto().getId(),
                        detalle.getProducto().getCodigo(), detalle.getProducto().getNombre(),
                        detalle.getCantidad()))
                .toList();
        return new TransferenciaDto(transferencia.getId(), transferencia.getNumero(),
                transferencia.getEstado(), transferencia.getFecha(),
                transferencia.getAlmacenOrigen().getId(), transferencia.getAlmacenOrigen().getNombre(),
                transferencia.getAlmacenDestino().getId(), transferencia.getAlmacenDestino().getNombre(),
                transferencia.getObservaciones(), detalles);
    }

    private BigDecimal costoPromedio(Stock stock, Long productoId, Long almacenId) {
        return movimientoRepository
                .findFirstByProductoIdAndAlmacenIdAndEliminadoFalseOrderByFechaDescIdDesc(productoId, almacenId)
                .map(MovimientoInventario::getCostoPromedio)
                .filter(costo -> costo != null)
                .orElseGet(() -> escalaCosto(stock.getProducto().getPrecioCompra()));
    }

    private void validarDisponible(Stock stock, BigDecimal cantidad) {
        BigDecimal disponible = stock.getCantidad().subtract(stock.getStockReservado());
        if (disponible.compareTo(cantidad) < 0) {
            throw new NegocioException("No hay stock disponible suficiente para realizar la operación.");
        }
    }

    private void validarCantidad(BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.scale() > 3) {
            throw new NegocioException("La cantidad debe ser mayor que cero y tener hasta tres decimales.");
        }
    }

    private BigDecimal escalaCosto(BigDecimal costo) {
        return costo == null ? COSTO_CERO : costo.setScale(2, RoundingMode.HALF_UP);
    }

    private String motivoSalida(MovimientoSolicitudDto solicitud) {
        return solicitud.motivo().name()
                + (solicitud.observaciones() == null || solicitud.observaciones().isBlank()
                        ? "" : ": " + solicitud.observaciones().trim());
    }

    private void exigirEstado(Transferencia transferencia, String esperado) {
        if (!esperado.equals(transferencia.getEstado())) {
            throw new NegocioException("La transferencia no permite esta operación en su estado actual.");
        }
    }

    private MovimientoDto movimientoDto(MovimientoInventario movimiento) {
        return new MovimientoDto(movimiento.getId(), movimiento.getReferencia(), movimiento.getFecha(),
                movimiento.getTipo(), movimiento.getProducto().getId(), movimiento.getProducto().getCodigo(),
                movimiento.getProducto().getNombre(), movimiento.getAlmacen().getId(),
                movimiento.getAlmacen().getNombre(), movimiento.getCantidad(), movimiento.getCostoUnitario(),
                movimiento.getSaldoCantidad(), movimiento.getCostoPromedio(),
                movimiento.getDocumentoReferencia(), movimiento.getMotivo(), movimiento.getCreadoPor());
    }
}
