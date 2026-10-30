package com.motoboutique.seguridad.service;

import com.motoboutique.seguridad.domain.Permiso;
import com.motoboutique.seguridad.domain.Rol;
import com.motoboutique.seguridad.dto.RolDto;
import com.motoboutique.seguridad.dto.RolMapper;
import com.motoboutique.seguridad.repository.PermisoRepository;
import com.motoboutique.seguridad.repository.RolRepository;
import com.motoboutique.shared.audit.AuditoriaService;
import com.motoboutique.shared.exception.NegocioException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final RolMapper rolMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional
    public RolDto asegurar(String codigo, String nombre, String descripcion, boolean sistema,
                           Collection<String> codigosPermisos) {
        Optional<Rol> existente = rolRepository.findByCodigo(codigo);
        Rol rol;
        if (existente.isPresent()) {
            rol = existente.get();
        } else {
            rol = new Rol();
            rol.setCodigo(codigo);
            rol.setNombre(nombre);
            rol.setDescripcion(descripcion);
            rol.setSistema(sistema);
            rol.setPermisos(resolverPermisos(codigosPermisos));
        }

        return rolMapper.toDto(rolRepository.save(rol));
    }

    @Override
    @Transactional
    public void agregarPermisos(String codigo, Collection<String> codigosPermisos) {
        if (codigosPermisos == null || codigosPermisos.isEmpty()) {
            return;
        }
        rolRepository.findByCodigo(codigo).ifPresent(rol -> {
            Set<String> actuales = rol.getPermisos().stream().map(Permiso::getCodigo)
                    .collect(Collectors.toSet());
            List<String> faltantes = codigosPermisos.stream()
                    .filter(permiso -> !actuales.contains(permiso))
                    .distinct()
                    .toList();
            if (faltantes.isEmpty()) {
                return;
            }
            rol.getPermisos().addAll(resolverPermisos(faltantes));
            rolRepository.save(rol);
        });
    }

    @Override
    public List<RolDto> listar() {
        return rolMapper.toDtoList(rolRepository.findByEliminadoFalseOrderByNombreAsc());
    }

    @Override
    public Optional<RolDto> buscarPorCodigo(String codigo) {
        return rolRepository.findByCodigo(codigo).map(rolMapper::toDto);
    }

    @Override
    @Transactional
    public RolDto actualizarPermisos(Long id, Collection<String> codigosPermisos) {
        Rol rol = rolRepository.findById(id)
                .filter(encontrado -> !encontrado.isEliminado())
                .orElseThrow(() -> new NegocioException("No se encontró el rol solicitado."));
        Set<Permiso> permisosNuevos = resolverPermisos(codigosPermisos);
        Set<String> actuales = rol.getPermisos().stream().map(Permiso::getCodigo)
                .collect(Collectors.toSet());
        Set<String> nuevos = permisosNuevos.stream().map(Permiso::getCodigo)
                .collect(Collectors.toSet());
        if ("ADMIN".equals(rol.getCodigo()) && !nuevos.containsAll(actuales)) {
            throw new NegocioException("No se pueden reducir los permisos del rol Administrador.");
        }
        if (actuales.contains("ROL_EDITAR") && !nuevos.contains("ROL_EDITAR")
                && rolRepository.contarRolesConPermiso("ROL_EDITAR") <= 1) {
            throw new NegocioException("No se puede quitar ROL_EDITAR al último rol que lo tiene.");
        }
        rol.setPermisos(permisosNuevos);
        Rol guardado = rolRepository.save(rol);
        auditoriaService.registrar("ACTUALIZAR_PERMISOS", "ROL", id.toString(),
                "Permisos: " + String.join(", ", nuevos.stream().sorted().toList()));
        return rolMapper.toDto(guardado);
    }

    private Set<Permiso> resolverPermisos(Collection<String> codigos) {
        Set<String> pedidos = codigos == null ? Set.of() : new LinkedHashSet<>(codigos);
        if (pedidos.isEmpty()) {
            return new HashSet<>();
        }
        List<Permiso> encontrados = permisoRepository.findByCodigoIn(pedidos);
        Set<String> codigosEncontrados = encontrados.stream()
                .map(Permiso::getCodigo)
                .collect(Collectors.toSet());
        List<String> faltantes = pedidos.stream()
                .filter(codigo -> !codigosEncontrados.contains(codigo))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new NegocioException("No existen los permisos: " + String.join(", ", faltantes));
        }
        return new HashSet<>(encontrados);
    }
}
