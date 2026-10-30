package com.motoboutique.inventario.service;

import com.motoboutique.catalogo.domain.Producto;
import com.motoboutique.catalogo.repository.ProductoRepository;
import com.motoboutique.inventario.domain.AjusteInventario;
import com.motoboutique.inventario.domain.Almacen;
import com.motoboutique.inventario.domain.ConteoDetalle;
import com.motoboutique.inventario.domain.ConteoFisico;
import com.motoboutique.inventario.domain.Stock;
import com.motoboutique.inventario.dto.AjusteInventarioDto;
import com.motoboutique.inventario.dto.AdministracionInventarioDto.AjusteDto;
import com.motoboutique.inventario.dto.AdministracionInventarioDto.AjusteSolicitud;
import com.motoboutique.inventario.dto.AdministracionInventarioDto.ConteoDto;
import com.motoboutique.inventario.dto.MovimientoDto;
import com.motoboutique.inventario.repository.AjusteInventarioRepository;
import com.motoboutique.inventario.repository.AlmacenRepository;
import com.motoboutique.inventario.repository.ConteoDetalleRepository;
import com.motoboutique.inventario.repository.ConteoFisicoRepository;
import com.motoboutique.inventario.repository.NumeracionDocumentoRepository;
import com.motoboutique.inventario.repository.MovimientoInventarioRepository;
import com.motoboutique.inventario.repository.StockRepository;
import com.motoboutique.seguridad.domain.Usuario;
import com.motoboutique.seguridad.repository.UsuarioRepository;
import com.motoboutique.shared.audit.AuditoriaService;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.exception.RecursoNoEncontradoException;
import com.motoboutique.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministracionInventarioService {

    private final ConteoFisicoRepository conteoRepository;
    private final ConteoDetalleRepository detalleRepository;
    private final AjusteInventarioRepository ajusteRepository;
    private final StockRepository stockRepository;
    private final ProductoRepository productoRepository;
    private final AlmacenRepository almacenRepository;
    private final UsuarioRepository usuarioRepository;
    private final NumeracionDocumentoRepository numeracionRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final MovimientoInventarioService movimientoService;
    private final AuditoriaService auditoriaService;
    private final AdministracionInventarioMapper mapper;

    @Transactional
    public ConteoDto abrirConteo(Long almacenId, Long usuarioId, String observaciones) {
        Almacen almacen = almacenRepository.findById(almacenId)
                .filter(a -> a.isActivo() && !a.isEliminado())
                .orElseThrow(() -> new NegocioException("El almacén no existe o está inactivo."));
        ConteoFisico conteo = new ConteoFisico();
        conteo.setNumero(numeracionRepository.siguienteNumero("CNT"));
        conteo.setAlmacen(almacen);
        conteo.setResponsable(usuarioRepository.getReferenceById(usuarioId));
        conteo.setObservaciones(observaciones);
        conteo = conteoRepository.save(conteo);

        var stocks = stockRepository.findByAlmacenIdAndEliminadoFalse(almacenId);
        var porProducto = stocks.stream().collect(java.util.stream.Collectors.toMap(
                stock -> stock.getProducto().getId(), Stock::getCantidad, (a, b) -> a));
        List<ConteoDetalle> detalles = new ArrayList<>();
        for (Producto producto : productoRepository.findByActivoTrueAndEliminadoFalseOrderByNombreAsc()) {
            ConteoDetalle detalle = new ConteoDetalle();
            detalle.setConteo(conteo);
            detalle.setProducto(producto);
            BigDecimal cantidad = porProducto.getOrDefault(producto.getId(), BigDecimal.ZERO);
            detalle.setStockSistema(cantidad);
            detalle.setStockContado(cantidad);
            detalle.setDiferencia(BigDecimal.ZERO.setScale(3));
            detalles.add(detalle);
        }
        detalleRepository.saveAll(detalles);
        auditoriaService.registrar("ABRIR_CONTEO", "CONTEO", conteo.getId().toString(),
                "Almacén " + almacen.getNombre());
        return mapper.conteo(conteo, detalles);
    }

    @Transactional
    public ConteoDto guardarConteo(Long id, Long productoId, BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() < 0 || cantidad.scale() > 3) {
            throw new NegocioException("La cantidad contada debe ser mayor o igual a cero y tener hasta tres decimales.");
        }
        obtenerConteoAbierto(id);
        ConteoDetalle detalle = detalleRepository
                .findByConteoIdAndProductoIdAndEliminadoFalse(id, productoId)
                .orElseThrow(() -> new NegocioException("El producto no pertenece a este conteo."));
        detalle.setStockContado(cantidad.setScale(3, java.math.RoundingMode.UNNECESSARY));
        detalle.setDiferencia(detalle.getStockContado().subtract(detalle.getStockSistema()));
        detalleRepository.save(detalle);
        return obtenerConteo(id);
    }

    @Transactional
    public ConteoDto cerrarConteo(Long id, Long usuarioId) {
        ConteoFisico conteo = obtenerConteoAbierto(id);
        List<ConteoDetalle> detalles = detalleRepository.findByConteoIdAndEliminadoFalseOrderByProductoNombre(id);
        Usuario solicitante = usuarioRepository.getReferenceById(usuarioId);
        for (ConteoDetalle detalle : detalles) {
            if (detalle.getDiferencia().signum() == 0) {
                continue;
            }
            AjusteInventario ajuste = new AjusteInventario();
            ajuste.setNumero(numeracionRepository.siguienteNumero("AJU"));
            ajuste.setProducto(detalle.getProducto());
            ajuste.setAlmacen(conteo.getAlmacen());
            ajuste.setCantidad(detalle.getDiferencia());
            ajuste.setMotivo("Diferencia de conteo físico " + conteo.getNumero());
            ajuste.setOrigen("CONTEO");
            ajuste.setSolicitante(solicitante);
            ajuste.setConteoDetalle(detalle);
            ajusteRepository.save(ajuste);
            detalle.setAjustado(true);
        }
        conteo.setEstado("CERRADO");
        conteo.setFechaCierre(Instant.now());
        auditoriaService.registrar("CERRAR_CONTEO", "CONTEO", id.toString(),
                "Ajustes pendientes generados para las diferencias.");
        return mapper.conteo(conteoRepository.save(conteo), detalles);
    }

    public PageResponse<ConteoDto> listarConteos(String estado, Long almacenId, Instant desde,
            Instant hasta, int page, int size) {
        validarPagina(page, size);
        Specification<ConteoFisico> spec = Specification.where(noEliminado())
                .and(textoIgual("estado", estado))
                .and((root, query, cb) -> almacenId == null ? cb.conjunction()
                        : cb.equal(root.get("almacen").get("id"), almacenId))
                .and((root, query, cb) -> desde == null ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("fechaApertura"), desde))
                .and((root, query, cb) -> hasta == null ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.get("fechaApertura"), hasta));
        var result = conteoRepository.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "fechaApertura")));
        return PageResponse.de(result, c -> mapper.conteo(c,
                detalleRepository.findByConteoIdAndEliminadoFalseOrderByProductoNombre(c.getId())));
    }

    public ConteoDto obtenerConteo(Long id) {
        ConteoFisico conteo = conteoRepository.findById(id)
                .filter(c -> !c.isEliminado())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Conteo", id));
        return mapper.conteo(conteo, detalleRepository
                .findByConteoIdAndEliminadoFalseOrderByProductoNombre(id));
    }

    @Transactional
    public AjusteDto solicitarAjuste(Long usuarioId, AjusteSolicitud solicitud) {
        if (solicitud.cantidad().signum() == 0) {
            throw new NegocioException("La cantidad del ajuste no puede ser cero.");
        }
        Producto producto = productoRepository.findByIdAndEliminadoFalse(solicitud.productoId())
                .filter(Producto::isActivo)
                .orElseThrow(() -> new NegocioException("El producto no existe o está inactivo."));
        Almacen almacen = almacenRepository.findById(solicitud.almacenId())
                .filter(a -> a.isActivo() && !a.isEliminado())
                .orElseThrow(() -> new NegocioException("El almacén no existe o está inactivo."));
        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setNumero(numeracionRepository.siguienteNumero("AJU"));
        ajuste.setProducto(producto);
        ajuste.setAlmacen(almacen);
        ajuste.setCantidad(solicitud.cantidad());
        ajuste.setMotivo(solicitud.motivo().trim());
        ajuste.setOrigen("MANUAL");
        ajuste.setSolicitante(usuarioRepository.getReferenceById(usuarioId));
        ajuste = ajusteRepository.save(ajuste);
        auditoriaService.registrar("SOLICITAR_AJUSTE", "AJUSTE", ajuste.getId().toString(),
                solicitud.motivo());
        return mapper.ajuste(ajuste);
    }

    public PageResponse<AjusteDto> listarAjustes(String estado, Long almacenId, Long productoId,
            Instant desde, Instant hasta, Long solicitanteId, int page, int size) {
        validarPagina(page, size);
        Specification<AjusteInventario> spec = Specification.where(noEliminadoAjuste())
                .and(textoIgual("estado", estado))
                .and((root, query, cb) -> almacenId == null ? cb.conjunction()
                        : cb.equal(root.get("almacen").get("id"), almacenId))
                .and((root, query, cb) -> productoId == null ? cb.conjunction()
                        : cb.equal(root.get("producto").get("id"), productoId))
                .and((root, query, cb) -> solicitanteId == null ? cb.conjunction()
                        : cb.equal(root.get("solicitante").get("id"), solicitanteId))
                .and((root, query, cb) -> desde == null ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("fechaSolicitud"), desde))
                .and((root, query, cb) -> hasta == null ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.get("fechaSolicitud"), hasta));
        return PageResponse.de(ajusteRepository.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "fechaSolicitud"))), mapper::ajuste);
    }

    @Transactional
    public AjusteDto aprobar(Long ajusteId, Long aprobadorId, boolean admin, String comentario) {
        AjusteInventario ajuste = ajustePendiente(ajusteId);
        if (!admin && ajuste.getSolicitante().getId().equals(aprobadorId)) {
            throw new NegocioException("No puedes aprobar tu propio ajuste.");
        }
        MovimientoDto movimiento = movimientoService.ajustar(new AjusteInventarioDto(
                ajuste.getProducto().getId(), ajuste.getAlmacen().getId(), ajuste.getCantidad(),
                ajuste.getCantidad().signum() > 0 ? ajuste.getProducto().getPrecioCompra() : null,
                ajuste.getMotivo(), ajuste.getNumero()));
        ajuste.setEstado("APROBADO");
        ajuste.setAprobador(usuarioRepository.getReferenceById(aprobadorId));
        ajuste.setComentario(comentario);
        ajuste.setFechaResolucion(Instant.now());
        ajuste.setMovimiento(movimientoRepository.findById(movimiento.id())
                .orElseThrow(() -> new NegocioException("No se encontró el movimiento generado.")));
        auditoriaService.registrar("APROBAR_AJUSTE", "AJUSTE", ajusteId.toString(), comentario);
        return mapper.ajuste(ajuste);
    }

    @Transactional
    public AjusteDto rechazar(Long ajusteId, Long aprobadorId, String comentario) {
        if (comentario == null || comentario.isBlank()) {
            throw new NegocioException("El comentario es obligatorio para rechazar un ajuste.");
        }
        AjusteInventario ajuste = ajustePendiente(ajusteId);
        ajuste.setEstado("RECHAZADO");
        ajuste.setAprobador(usuarioRepository.getReferenceById(aprobadorId));
        ajuste.setComentario(comentario.trim());
        ajuste.setFechaResolucion(Instant.now());
        auditoriaService.registrar("RECHAZAR_AJUSTE", "AJUSTE", ajusteId.toString(), comentario);
        return mapper.ajuste(ajuste);
    }

    @Transactional
    public List<AjusteDto> resolverLote(List<Long> ids, Long aprobadorId, boolean admin,
            String accion, String comentario) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw new NegocioException("Selecciona entre uno y cien ajustes.");
        }
        List<AjusteDto> resultados = new ArrayList<>();
        for (Long id : ids) {
            resultados.add("APROBAR".equals(accion)
                    ? aprobar(id, aprobadorId, admin, comentario)
                    : rechazar(id, aprobadorId, comentario));
        }
        return resultados;
    }

    private ConteoFisico obtenerConteoAbierto(Long id) {
        ConteoFisico conteo = conteoRepository.findById(id)
                .filter(c -> !c.isEliminado())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Conteo", id));
        if (!"ABIERTO".equals(conteo.getEstado())) {
            throw new NegocioException("El conteo ya está cerrado.");
        }
        return conteo;
    }

    private AjusteInventario ajustePendiente(Long id) {
        AjusteInventario ajuste = ajusteRepository.findById(id)
                .filter(a -> !a.isEliminado())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Ajuste", id));
        if (!"PENDIENTE".equals(ajuste.getEstado())) {
            throw new NegocioException("El ajuste ya fue resuelto.");
        }
        return ajuste;
    }

    private Specification<ConteoFisico> noEliminado() {
        return (root, query, cb) -> cb.isFalse(root.get("eliminado"));
    }

    private Specification<AjusteInventario> noEliminadoAjuste() {
        return (root, query, cb) -> cb.isFalse(root.get("eliminado"));
    }

    private <T> Specification<T> textoIgual(String campo, String valor) {
        return (root, query, cb) -> valor == null || valor.isBlank()
                ? cb.conjunction() : cb.equal(root.get(campo), valor);
    }

    private void validarPagina(int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new NegocioException("La página o el tamaño no son válidos.");
        }
    }

}
