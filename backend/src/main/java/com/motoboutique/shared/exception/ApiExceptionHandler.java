package com.motoboutique.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo global de errores de la API REST. Responde con ProblemDetail (RFC 9457), el formato
 * estándar de errores de Spring, que el frontend interpreta (título, detalle, errores por
 * campo y referencia). Antes se aplicaba solo a los @RestController internos; ahora toda la
 * aplicación es una API, así que cubre todos los controllers.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail manejarNegocio(NegocioException ex) {
        return problema(422, ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return problema(404, ex.getMessage());
    }

    @ExceptionHandler(CorreoNoDisponibleException.class)
    public ProblemDetail manejarCorreoNoDisponible(CorreoNoDisponibleException ex) {
        return problema(503, "No se pudo enviar el correo de recuperación. Inténtalo más tarde.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problema = problema(400, "Hay datos no válidos en la solicitud.");
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail manejarBloqueoOptimista(OptimisticLockingFailureException ex) {
        return problema(409, MensajesHttp.detalle(409));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return problema(409, "No se pudo guardar: el dato ya existe o está relacionado con otros registros.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex) {
        return problema(403, MensajesHttp.detalle(403));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarGeneral(Exception ex, HttpServletRequest request) {
        int codigo = ex instanceof ErrorResponse respuesta ? respuesta.getStatusCode().value() : 500;
        if (codigo < 500) {
            return problema(codigo, MensajesHttp.detalle(codigo));
        }
        String referencia = MensajesHttp.nuevaReferencia();
        log.error("Error inesperado [ref {}] en {} {}", referencia,
                request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problema = problema(codigo, MensajesHttp.detalle(codigo));
        problema.setProperty("referencia", referencia);
        return problema;
    }

    private ProblemDetail problema(int codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(codigo), detalle);
        problema.setTitle(MensajesHttp.titulo(codigo));
        return problema;
    }
}
