package com.motoboutique.catalogo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motoboutique.catalogo.domain.Categoria;
import com.motoboutique.catalogo.domain.Marca;
import com.motoboutique.catalogo.dto.CategoriaDto;
import com.motoboutique.catalogo.dto.CategoriaGuardarDto;
import com.motoboutique.catalogo.dto.MarcaDto;
import com.motoboutique.catalogo.dto.MarcaGuardarDto;
import com.motoboutique.catalogo.dto.ProductoGuardarDto;
import com.motoboutique.catalogo.repository.CategoriaRepository;
import com.motoboutique.catalogo.repository.MarcaRepository;
import com.motoboutique.catalogo.repository.ModeloMotoRepository;
import com.motoboutique.catalogo.repository.ProductoRepository;
import com.motoboutique.inventario.repository.AlmacenRepository;
import com.motoboutique.inventario.repository.StockRepository;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceImplTest {
    @Mock private CategoriaRepository categorias;
    @Mock private MarcaRepository marcas;
    @Mock private ModeloMotoRepository modelos;
    @Mock private ProductoRepository productos;
    @Mock private StockRepository stock;
    @Mock private AlmacenRepository almacenes;
    @InjectMocks private CatalogoServiceImpl servicio;

    @Test
    void rechazaCategoriaDuplicada() {
        when(categorias.existsByNombreIgnoreCaseAndEliminadoFalse("Motos")).thenReturn(true);

        assertThrows(NegocioException.class,
                () -> servicio.crearCategoria(new CategoriaGuardarDto("Motos", "", true)));
    }

    @Test
    void permiteRecrearUnaCategoriaEliminada() {
        // Solo se compara con categorías no eliminadas: una "Frenos" borrada no bloquea.
        when(categorias.existsByNombreIgnoreCaseAndEliminadoFalse("Frenos")).thenReturn(false);
        when(categorias.save(any(Categoria.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        CategoriaDto creada = servicio.crearCategoria(new CategoriaGuardarDto(" Frenos ", "", true));

        assertEquals("Frenos", creada.nombre());
    }

    @Test
    void permiteRecrearUnaMarcaEliminada() {
        when(marcas.existsByNombreIgnoreCaseAndEliminadoFalse("Brembo")).thenReturn(false);
        when(marcas.save(any(Marca.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        MarcaDto creada = servicio.crearMarca(new MarcaGuardarDto("Brembo", "", true));

        assertEquals("Brembo", creada.nombre());
    }

    @Test
    void validaCategoriaAntesDeCalcularPrecio() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.crearProducto(new ProductoGuardarDto(1L, null, "P1", "Producto",
                        null, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, "UNIDAD", true,
                        null)));
    }

    @Test
    void rechazaCodigoDeProductoDuplicado() {
        when(productos.existsByCodigoIgnoreCase("P-100")).thenReturn(true);

        assertThrows(NegocioException.class,
                () -> servicio.crearProducto(new ProductoGuardarDto(1L, null, "P-100", "Producto",
                        null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, "UNIDAD", true,
                        null)));
    }

    @Test
    void noEliminaCategoriaConProductosActivos() {
        Categoria categoria = new Categoria();
        when(categorias.findByIdAndEliminadoFalse(4L)).thenReturn(Optional.of(categoria));
        when(productos.countByCategoriaIdAndActivoTrueAndEliminadoFalse(4L)).thenReturn(1L);

        assertThrows(NegocioException.class, () -> servicio.eliminarCategoria(4L));
    }

    @Test
    void eliminarCategoriaSePermiteCuandoNoTieneProductosActivos() {
        Categoria categoria = new Categoria();
        when(categorias.findByIdAndEliminadoFalse(4L)).thenReturn(Optional.of(categoria));
        when(productos.countByCategoriaIdAndActivoTrueAndEliminadoFalse(4L)).thenReturn(0L);

        servicio.eliminarCategoria(4L);

        verify(categorias).findByIdAndEliminadoFalse(4L);
    }
}
