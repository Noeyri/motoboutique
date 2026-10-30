package com.motoboutique.catalogo.service;

import com.motoboutique.catalogo.domain.*;
import com.motoboutique.catalogo.dto.*;
import com.motoboutique.catalogo.repository.*;
import com.motoboutique.inventario.domain.Stock;
import com.motoboutique.inventario.repository.AlmacenRepository;
import com.motoboutique.inventario.repository.StockRepository;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.exception.RecursoNoEncontradoException;
import com.motoboutique.shared.web.PageResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogoServiceImpl implements CatalogoService {
    private final CategoriaRepository categorias;
    private final MarcaRepository marcas;
    private final ModeloMotoRepository modelos;
    private final ProductoRepository productos;
    private final StockRepository stock;
    private final AlmacenRepository almacenes;

    CatalogoServiceImpl(CategoriaRepository categorias, MarcaRepository marcas,
                        ModeloMotoRepository modelos, ProductoRepository productos) {
        this(categorias, marcas, modelos, productos, null);
    }

    @Autowired
    CatalogoServiceImpl(CategoriaRepository categorias, MarcaRepository marcas,
                        ModeloMotoRepository modelos, ProductoRepository productos,
                        StockRepository stock, AlmacenRepository almacenes) {
        this.categorias = categorias;
        this.marcas = marcas;
        this.modelos = modelos;
        this.productos = productos;
        this.stock = stock;
        this.almacenes = almacenes;
    }

    CatalogoServiceImpl(CategoriaRepository categorias, MarcaRepository marcas,
                        ModeloMotoRepository modelos, ProductoRepository productos,
                        StockRepository stock) {
        this(categorias, marcas, modelos, productos, stock, null);
    }

    @Override
    public PageResponse<CategoriaDto> listarCategorias(String q, Boolean activo, Pageable pageable) {
        Specification<Categoria> spec = texto((root, cb, value) -> cb.or(cb.like(cb.lower(root.get("nombre")), value), cb.like(cb.lower(root.get("descripcion")), value)), q);
        spec = spec.and((root, query, cb) -> cb.isFalse(root.get("eliminado")));
        if (activo != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        return PageResponse.de(categorias.findAll(spec, pageable), this::categoriaDto);
    }

    @Override @Transactional public CategoriaDto crearCategoria(CategoriaGuardarDto d) { return categoriaDto(categorias.save(aplicarCategoria(new Categoria(), d))); }
    @Override @Transactional public CategoriaDto actualizarCategoria(Long id, CategoriaGuardarDto d) { Categoria c = categoria(id); categorias.findByNombreIgnoreCaseAndEliminadoFalse(d.nombre().trim()).filter(x -> !x.getId().equals(id)).ifPresent(x -> { throw nuevoCodigo("Ya existe una categoría con ese nombre."); }); return categoriaDto(categorias.save(aplicarCategoria(c, d))); }
    @Override @Transactional public void eliminarCategoria(Long id) { Categoria c = categoria(id); if (productos.countByCategoriaIdAndActivoTrueAndEliminadoFalse(id) > 0) throw new NegocioException("No se puede eliminar una categoría con productos activos."); c.marcarEliminado(); }

    private Categoria categoria(Long id) { return categorias.findByIdAndEliminadoFalse(id).orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", id)); }
    private Categoria aplicarCategoria(Categoria c, CategoriaGuardarDto d) { String nombre = d.nombre().trim(); if (categorias.existsByNombreIgnoreCaseAndEliminadoFalse(nombre) && !nombre.equalsIgnoreCase(c.getNombre())) throw nuevoCodigo("Ya existe una categoría con ese nombre."); c.setNombre(nombre); c.setDescripcion(d.descripcion()); c.setActivo(d.activo() == null || d.activo()); return c; }
    private CategoriaDto categoriaDto(Categoria c) { return new CategoriaDto(c.getId(), c.getNombre(), c.getDescripcion(), c.isActivo()); }

    @Override public PageResponse<MarcaDto> listarMarcas(String q, Boolean activo, Pageable pageable) {
        Specification<Marca> spec = Specification.where((root, query, cb) -> cb.isFalse(root.get("eliminado")));
        spec = spec.and(texto((root, cb, value) -> cb.or(cb.like(cb.lower(root.get("nombre")), value), cb.like(cb.lower(root.get("descripcion")), value)), q));
        if (activo != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        return PageResponse.de(marcas.findAll(spec, pageable), this::marcaDto);
    }
    @Override @Transactional public MarcaDto crearMarca(MarcaGuardarDto d) { return marcaDto(marcas.save(aplicarMarca(new Marca(), d))); }
    @Override @Transactional public MarcaDto actualizarMarca(Long id, MarcaGuardarDto d) { Marca m = marca(id); marcas.findByNombreIgnoreCaseAndEliminadoFalse(d.nombre().trim()).filter(x -> !x.getId().equals(id)).ifPresent(x -> { throw nuevoCodigo("Ya existe una marca con ese nombre."); }); return marcaDto(marcas.save(aplicarMarca(m, d))); }
    @Override @Transactional public void eliminarMarca(Long id) { Marca m = marca(id); if (productos.countByMarcaIdAndActivoTrueAndEliminadoFalse(id) > 0) throw new NegocioException("No se puede eliminar una marca con productos activos."); m.marcarEliminado(); }
    private Marca marca(Long id) { return marcas.findByIdAndEliminadoFalse(id).orElseThrow(() -> RecursoNoEncontradoException.de("Marca", id)); }
    private Marca aplicarMarca(Marca m, MarcaGuardarDto d) { String nombre = d.nombre().trim(); if (marcas.existsByNombreIgnoreCaseAndEliminadoFalse(nombre) && !nombre.equalsIgnoreCase(m.getNombre())) throw nuevoCodigo("Ya existe una marca con ese nombre."); m.setNombre(nombre); m.setDescripcion(d.descripcion()); m.setActivo(d.activo() == null || d.activo()); return m; }
    private MarcaDto marcaDto(Marca m) { return new MarcaDto(m.getId(), m.getNombre(), m.getDescripcion(), m.isActivo()); }

    @Override public PageResponse<ProductoDto> listarProductos(String q, Long categoriaId, Long marcaId, Boolean activo, Boolean stockBajo, Long modeloMotoId, Pageable pageable) {
        Specification<Producto> spec = Specification.where((root, query, cb) -> cb.isFalse(root.get("eliminado")));
        spec = spec.and(texto((root, cb, value) -> cb.or(cb.like(cb.lower(root.get("codigo")), value), cb.like(cb.lower(root.get("nombre")), value)), q));
        if (categoriaId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId));
        if (marcaId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("marca").get("id"), marcaId));
        if (activo != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        if (modeloMotoId != null) spec = spec.and((root, query, cb) -> {
            Join<Object, Object> compatibilidad = root.join("compatibilidades");
            query.distinct(true);
            return cb.equal(compatibilidad.get("id"), modeloMotoId);
        });
        if (stockBajo != null) {
            spec = spec.and((root, query, cb) -> {
                Subquery<BigDecimal> existencia = query.subquery(BigDecimal.class);
                var stockRoot = existencia.from(Stock.class);
                existencia.select(cb.coalesce(cb.sum(stockRoot.get("cantidad")), BigDecimal.ZERO));
                existencia.where(
                        cb.equal(stockRoot.get("producto").get("id"), root.get("id")),
                        cb.isFalse(stockRoot.get("eliminado")));
                return stockBajo
                        ? cb.lessThanOrEqualTo(existencia, root.get("stockMinimo"))
                        : cb.greaterThan(existencia, root.get("stockMinimo"));
            });
        }
        return PageResponse.de(productos.findAll(spec, pageable), this::productoDto);
    }

    @Override public ProductoDto obtenerProducto(Long id) { return productoDto(producto(id)); }
    private Producto producto(Long id) { return productos.findByIdAndEliminadoFalse(id).orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id)); }
    @Override @Transactional public ProductoDto crearProducto(ProductoGuardarDto d) { return productoDto(productos.save(aplicarProducto(new Producto(), d))); }
    @Override @Transactional public ProductoDto actualizarProducto(Long id, ProductoGuardarDto d) { Producto p = producto(id); return productoDto(productos.save(aplicarProducto(p, d))); }
    @Override @Transactional public void eliminarProducto(Long id) { producto(id).marcarEliminado(); }
    @Override @Transactional public void actualizarCompatibilidades(Long productoId, List<Long> modeloIds) { Producto p = producto(productoId); p.setCompatibilidades(new HashSet<>(resolverModelos(modeloIds))); }

    private Producto aplicarProducto(Producto p, ProductoGuardarDto d) {
        String codigo = d.codigo().trim();
        if (productos.existsByCodigoIgnoreCase(codigo) && !codigo.equalsIgnoreCase(p.getCodigo())) throw nuevoCodigo("Ya existe un producto con ese código.");
        Categoria categoria = categoria(d.categoriaId());
        if (d.precioVenta().compareTo(d.precioCompra()) < 0) throw new NegocioException("El precio de venta no puede ser menor que el precio de compra.");
        p.setCategoria(categoria);
        p.setMarca(d.marcaId() == null ? null : marca(d.marcaId()));
        p.setCodigo(codigo); p.setNombre(d.nombre().trim()); p.setDescripcion(d.descripcion());
        p.setPrecioCompra(d.precioCompra()); p.setPrecioVenta(d.precioVenta()); p.setStockMinimo(d.stockMinimo());
        p.setUnidad(d.unidad().trim().toUpperCase()); p.setActivo(d.activo() == null || d.activo());
        p.setImagenUrl(d.imagenUrl()); p.setEspecificaciones(d.especificaciones() == null ? java.util.Map.of() : d.especificaciones());
        p.setCompatibilidades(new HashSet<>(resolverModelos(d.compatibilidadIds())));
        return p;
    }
    private Set<ModeloMoto> resolverModelos(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<Long> distintos = new LinkedHashSet<>(ids);
        List<ModeloMoto> encontrados = modelos.findAllById(distintos).stream()
                .filter(modelo -> !modelo.isEliminado())
                .toList();
        if (encontrados.size() != distintos.size()) {
            throw new NegocioException("Uno o más modelos de moto no existen o están eliminados.");
        }
        return new HashSet<>(encontrados);
    }

    private ProductoDto productoDto(Producto p) {
        List<Stock> cantidades = stock == null ? List.of() : stock.findByProductoIdAndEliminadoFalse(p.getId());
        BigDecimal total = cantidades.stream().map(Stock::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<StockProductoDto> porAlmacen = cantidades.stream()
                .map(s -> new StockProductoDto(s.getAlmacen().getId(),
                        almacenes == null ? null : almacenes.findById(s.getAlmacen().getId())
                                .map(almacen -> almacen.getNombre()).orElse(null),
                        s.getCantidad(), s.getStockReservado()))
                .toList();
        Set<Long> compatibles = p.getCompatibilidades().stream().map(ModeloMoto::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<ModeloMotoDto> detalleCompatibilidades = p.getCompatibilidades().stream()
                .map(this::modeloDto)
                .toList();
        return new ProductoDto(p.getId(), p.getCategoria().getId(), p.getCategoria().getNombre(), p.getMarca() == null ? null : p.getMarca().getId(), p.getMarca() == null ? null : p.getMarca().getNombre(), p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getPrecioCompra(), p.getPrecioVenta(), p.getStockMinimo(), p.getUnidad(), p.isActivo(), p.getImagenUrl(), p.getEspecificaciones(), compatibles, detalleCompatibilidades, total, porAlmacen);
    }

    @Override public PageResponse<ModeloMotoDto> listarModelos(String q, Long marcaId, Integer anio, Pageable pageable) {
        Specification<ModeloMoto> spec = Specification.where((root, query, cb) -> cb.isFalse(root.get("eliminado")));
        spec = spec.and(texto((root, cb, value) -> cb.like(cb.lower(root.get("nombre")), value), q));
        if (marcaId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("marca").get("id"), marcaId));
        if (anio != null) spec = spec.and((root, query, cb) -> cb.and(cb.or(cb.isNull(root.get("anioInicio")), cb.lessThanOrEqualTo(root.get("anioInicio"), anio)), cb.or(cb.isNull(root.get("anioFin")), cb.greaterThanOrEqualTo(root.get("anioFin"), anio))));
        return PageResponse.de(modelos.findAll(spec, pageable), this::modeloDto);
    }
    @Override public ModeloMotoDto obtenerModelo(Long id) { return modeloDto(modelo(id)); }
    @Override @Transactional public ModeloMotoDto crearModelo(ModeloMotoGuardarDto d) { ModeloMoto m = new ModeloMoto(); validarModelo(d); aplicarModelo(m, d); return modeloDto(modelos.save(m)); }
    @Override @Transactional public ModeloMotoDto actualizarModelo(Long id, ModeloMotoGuardarDto d) { ModeloMoto m = modelo(id); validarModelo(d); aplicarModelo(m, d); return modeloDto(modelos.save(m)); }
    @Override @Transactional public void eliminarModelo(Long id) { modelo(id).marcarEliminado(); }
    private ModeloMoto modelo(Long id) { return modelos.findByIdAndEliminadoFalse(id).orElseThrow(() -> RecursoNoEncontradoException.de("Modelo de moto", id)); }
    private void validarModelo(ModeloMotoGuardarDto d) { if (d.anioInicio() != null && d.anioFin() != null && d.anioFin() < d.anioInicio()) throw new NegocioException("El año final no puede ser menor que el año inicial."); }
    private void aplicarModelo(ModeloMoto m, ModeloMotoGuardarDto d) { m.setMarca(marca(d.marcaId())); m.setNombre(d.nombre().trim()); m.setAnioInicio(d.anioInicio()); m.setAnioFin(d.anioFin()); m.setCilindradaCc(d.cilindradaCc()); m.setTipo(d.tipo()); }
    private ModeloMotoDto modeloDto(ModeloMoto m) { return new ModeloMotoDto(m.getId(), m.getMarca().getId(), m.getMarca().getNombre(), m.getNombre(), m.getAnioInicio(), m.getAnioFin(), m.getCilindradaCc(), m.getTipo()); }
    @Override public List<ProductoDto> productosDeModelo(Long modeloId) { modelo(modeloId); return productos.findAll((root, query, cb) -> { Join<Object, Object> compatibilidad = root.join("compatibilidades"); return cb.and(cb.isFalse(root.get("eliminado")), cb.equal(compatibilidad.get("id"), modeloId)); }).stream().map(this::productoDto).toList(); }
    @Override public List<MarcaModeloArbolDto> marcasConModelos() {
        List<ModeloMoto> modelosActivos = modelos.findByEliminadoFalseAndMarcaActivoTrueOrderByNombre();
        return marcas.findByEliminadoFalseAndActivoTrueOrderByNombre().stream()
                .map(marca -> new MarcaModeloArbolDto(marca.getId(), marca.getNombre(),
                        modelosActivos.stream()
                                .filter(modelo -> modelo.getMarca().getId().equals(marca.getId()))
                                .map(this::modeloDto)
                                .toList()))
                .toList();
    }
    @Override public List<Integer> aniosModelo(Long modeloId) { ModeloMoto m = modelo(modeloId); int inicio = m.getAnioInicio() == null ? 0 : m.getAnioInicio(); int fin = m.getAnioFin() == null ? inicio : m.getAnioFin(); if (inicio == 0) return List.of(); java.util.ArrayList<Integer> anios = new java.util.ArrayList<>(); for (int anio = inicio; anio <= fin; anio++) anios.add(anio); return anios; }

    private NegocioException nuevoCodigo(String mensaje) { return new NegocioException(mensaje); }
    private static <T> Specification<T> texto(Texto<T> comparador, String q) { if (q == null || q.isBlank()) return Specification.where((root, query, cb) -> cb.conjunction()); String valor = "%" + q.trim().toLowerCase() + "%"; return (root, query, cb) -> comparador.aplicar(root, cb, valor); }
    @FunctionalInterface private interface Texto<T> { jakarta.persistence.criteria.Predicate aplicar(jakarta.persistence.criteria.Root<T> root, jakarta.persistence.criteria.CriteriaBuilder cb, String valor); }
}