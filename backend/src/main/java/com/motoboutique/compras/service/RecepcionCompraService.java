package com.motoboutique.compras.service;

import com.motoboutique.compras.domain.OrdenCompra;
import com.motoboutique.compras.domain.OrdenCompraDetalle;
import com.motoboutique.compras.domain.RecepcionCompra;
import com.motoboutique.compras.domain.RecepcionDetalle;
import com.motoboutique.compras.dto.RecepcionDetalleDto;
import com.motoboutique.compras.dto.RecepcionDetalleSolicitudDto;
import com.motoboutique.compras.dto.RecepcionDto;
import com.motoboutique.compras.dto.RecepcionGuardarDto;
import com.motoboutique.compras.repository.OrdenCompraDetalleRepository;
import com.motoboutique.compras.repository.OrdenCompraRepository;
import com.motoboutique.compras.repository.RecepcionCompraRepository;
import com.motoboutique.compras.repository.RecepcionDetalleRepository;
import com.motoboutique.inventario.service.MovimientoInventarioService;
import com.motoboutique.inventario.repository.NumeracionDocumentoRepository;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.exception.RecursoNoEncontradoException;
import com.motoboutique.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecepcionCompraService {
    private static final Set<String> ORDENES = Set.of("fecha", "numero", "orden.numero");
    private final OrdenCompraRepository ordenRepository;
    private final OrdenCompraDetalleRepository ordenDetalleRepository;
    private final RecepcionCompraRepository recepcionRepository;
    private final RecepcionDetalleRepository recepcionDetalleRepository;
    private final NumeracionDocumentoRepository numeracionRepository;
    private final MovimientoInventarioService movimientoService;

    @Transactional(readOnly = true)
    public PageResponse<RecepcionDto> listar(Long ordenId, Instant desde, Instant hasta,
                                             int page, int size, String sort, String direction) {
        validarFechas(desde, hasta);
        Specification<RecepcionCompra> specification = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.isFalse(root.get("eliminado")));
            if (ordenId != null) {
                condiciones.add(cb.equal(root.get("orden").get("id"), ordenId));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        Page<RecepcionCompra> resultado = recepcionRepository.findAll(specification,
                pagina(page, size, sort, direction));
        return PageResponse.de(resultado, this::dto);
    }

    @Transactional
    public RecepcionDto recibir(RecepcionGuardarDto solicitud) {
        OrdenCompra orden = ordenRepository.bloquearActiva(solicitud.ordenId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Orden de compra", solicitud.ordenId()));
        if (!"CONFIRMADA".equals(orden.getEstado())) {
            throw new NegocioException("Solo se pueden recibir órdenes en estado CONFIRMADA.");
        }
        Set<Long> ids = new HashSet<>();
        List<OrdenCompraDetalle> lineas = new ArrayList<>();
        for (RecepcionDetalleSolicitudDto solicitada : solicitud.detalles()) {
            if (!ids.add(solicitada.detalleOrdenId())) {
                throw new NegocioException("No repitas un detalle dentro de la recepción.");
            }
            OrdenCompraDetalle detalle = ordenDetalleRepository
                    .findByIdAndOrdenIdAndEliminadoFalse(solicitada.detalleOrdenId(), orden.getId())
                    .orElseThrow(() -> new NegocioException(
                            "Uno de los productos no pertenece a esta orden de compra."));
            if (solicitada.cantidad().compareTo(
                    detalle.getCantidad().subtract(detalle.getCantidadRecibida())) > 0) {
                throw new NegocioException("La cantidad recibida supera lo pendiente para el producto "
                        + detalle.getProducto().getCodigo() + ".");
            }
            lineas.add(detalle);
        }
        lineas.sort(Comparator.comparing(detalle -> detalle.getProducto().getId()));

        RecepcionCompra recepcion = new RecepcionCompra();
        recepcion.setOrden(orden);
        recepcion.setNumero(numeracionRepository.siguienteNumero("REC"));
        recepcion.setObservaciones(solicitud.observaciones());
        recepcion = recepcionRepository.saveAndFlush(recepcion);

        for (OrdenCompraDetalle detalle : lineas) {
            RecepcionDetalleSolicitudDto solicitada = solicitud.detalles().stream()
                    .filter(linea -> linea.detalleOrdenId().equals(detalle.getId()))
                    .findFirst().orElseThrow();
            movimientoService.registrarRecepcion(detalle.getProducto().getId(),
                    orden.getAlmacen().getId(), solicitada.cantidad(), detalle.getCostoUnitario(),
                    recepcion.getNumero(), orden.getNumero());
            RecepcionDetalle recibida = new RecepcionDetalle();
            recibida.setRecepcion(recepcion);
            recibida.setDetalleOrden(detalle);
            recibida.setCantidad(solicitada.cantidad());
            recepcionDetalleRepository.save(recibida);
            detalle.setCantidadRecibida(detalle.getCantidadRecibida().add(solicitada.cantidad()));
        }
        recepcionDetalleRepository.flush();
        ordenDetalleRepository.flush();

        boolean completa = ordenDetalleRepository.findByOrdenIdAndEliminadoFalseOrderById(orden.getId())
                .stream().allMatch(detalle ->
                        detalle.getCantidadRecibida().compareTo(detalle.getCantidad()) >= 0);
        if (completa) {
            orden.setEstado("RECIBIDA");
        }
        return dto(recepcion);
    }

    private RecepcionDto dto(RecepcionCompra recepcion) {
        List<RecepcionDetalleDto> detalles = recepcionDetalleRepository
                .findByRecepcionIdAndEliminadoFalseOrderById(recepcion.getId()).stream()
                .map(detalle -> new RecepcionDetalleDto(detalle.getDetalleOrden().getId(),
                        detalle.getDetalleOrden().getProducto().getId(),
                        detalle.getDetalleOrden().getProducto().getCodigo(),
                        detalle.getDetalleOrden().getProducto().getNombre(), detalle.getCantidad()))
                .toList();
        OrdenCompra orden = recepcion.getOrden();
        return new RecepcionDto(recepcion.getId(), recepcion.getNumero(), orden.getId(),
                orden.getNumero(), orden.getProveedor().getRazonSocial(), recepcion.getFecha(),
                recepcion.getObservaciones(), recepcion.getCreadoPor(), detalles);
    }

    private Pageable pagina(int page, int size, String sort, String direction) {
        String columna = sort != null && ORDENES.contains(sort) ? sort : "fecha";
        Sort.Direction sentido = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)),
                Sort.by(sentido, columna));
    }

    private void validarFechas(Instant desde, Instant hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
    }
}
