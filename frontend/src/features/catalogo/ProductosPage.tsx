
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '../../auth/AuthContext'
import { catalogoApi } from '../../api/catalogo'
import type { Categoria, Marca, Producto, ProductoGuardar } from '../../api/types'
import { ApiError } from '../../api/client'
import { Badge } from '../../components/Badge'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorState } from '../../components/ErrorState'
import { FormModal } from '../../components/FormModal'
import { Pagination } from '../../components/Pagination'
import { SearchInput } from '../../components/SearchInput'
import { Skeleton } from '../../components/Skeleton'

interface FormularioProducto {
  codigo: string
  nombre: string
  categoriaId: string
  marcaId: string
  precioCompra: string
  precioVenta: string
  stockMinimo: string
  unidad: string
  imagenUrl: string
  descripcion: string
  especificaciones: Array<{ caracteristica: string; detalle: string }>
  compatibilidadIds: number[]
  activo: boolean
}

const inicial: FormularioProducto = {
  codigo: '',
  nombre: '',
  categoriaId: '',
  marcaId: '',
  precioCompra: '0',
  precioVenta: '0',
  stockMinimo: '0',
  unidad: 'UNIDAD',
  imagenUrl: '',
  descripcion: '',
  especificaciones: [],
  compatibilidadIds: [],
  activo: true,
}

export function ProductosPage() {
  const [params, setParams] = useSearchParams()
  const [editar, setEditar] = useState<Producto | 'nuevo' | null>(null)
  const [form, setForm] = useState(inicial)
  const [eliminar, setEliminar] = useState<Producto | null>(null)
  const [nombreCategoria, setNombreCategoria] = useState('')
  const [nombreMarca, setNombreMarca] = useState('')

  const [gestion, setGestion] = useState<{
    tipo: 'categoria' | 'marca'
    registro: Categoria | Marca
  } | null>(null)

  const [editarReferencia, setEditarReferencia] = useState<{
    tipo: 'categoria' | 'marca'
    registro: Categoria | Marca
  } | null>(null)

  const [nombreReferencia, setNombreReferencia] = useState('')

  const { tienePermiso } = useAuth()
  const puedeEditar = tienePermiso('PRODUCTO_EDITAR')
  const queryClient = useQueryClient()

  const page = Math.max(0, Number(params.get('page') ?? 0))
  const size = Number(params.get('size') ?? 20)

  const filtros = {
    q: params.get('q') ?? undefined,
    page,
    size,
    categoriaId: params.get('categoriaId')
      ? Number(params.get('categoriaId'))
      : undefined,
    marcaId: params.get('marcaId')
      ? Number(params.get('marcaId'))
      : undefined,
    activo: params.has('activo')
      ? params.get('activo') === 'true'
      : undefined,
    stockBajo: params.get('stockBajo') === 'true'
      ? true
      : undefined,
    sort: params.get('sort') ?? 'nombre',
  }

  const productos = useQuery({
    queryKey: ['catalogo-productos', filtros],
    queryFn: () => catalogoApi.productos(filtros),
  })

  const categorias = useQuery({
    queryKey: ['categorias-opciones'],
    queryFn: () => catalogoApi.categorias({ page: 0, size: 200 }),
  })

  const marcas = useQuery({
    queryKey: ['marcas-opciones'],
    queryFn: () => catalogoApi.marcas({ page: 0, size: 200 }),
  })

  const modelos = useQuery({
    queryKey: ['modelos-opciones'],
    queryFn: () => catalogoApi.modelos({ page: 0, size: 200 }),
  })

  const refrescar = async () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: ['catalogo-productos'],
      }),
      queryClient.invalidateQueries({
        queryKey: ['productos-opciones'],
      }),
    ])

  const guardar = useMutation({
    mutationFn: (datos: ProductoGuardar) =>
      editar === 'nuevo' || !editar
        ? catalogoApi.crearProducto(datos)
        : catalogoApi.actualizarProducto(editar.id, datos),

    onSuccess: async () => {
      await refrescar()
      setEditar(null)
      toast.success('Producto guardado.')
    },

    onError: (error) =>
      toast.error(
        error instanceof ApiError
          ? error.detalle
          : 'No se pudo guardar el producto.'
      ),
  })

  const borrar = useMutation({
    mutationFn: (producto: Producto) =>
      catalogoApi.eliminarProducto(producto.id),

    onSuccess: async () => {
      await refrescar()
      setEliminar(null)
      toast.success('Producto eliminado.')
    },

    onError: (error) =>
      toast.error(
        error instanceof ApiError
          ? error.detalle
          : 'No se pudo eliminar el producto.'
      ),
  })

  const crearCategoria = useMutation({
    mutationFn: () =>
      catalogoApi.crearCategoria({
        nombre: nombreCategoria.trim(),
      }),

    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['categorias-opciones'],
      })
      setNombreCategoria('')
      toast.success('Categoría creada.')
    },

    onError: (error) =>
      toast.error(
        error instanceof ApiError
          ? error.detalle
          : 'No se pudo crear la categoría.'
      ),
  })

  const crearMarca = useMutation({
    mutationFn: () =>
      catalogoApi.crearMarca({
        nombre: nombreMarca.trim(),
      }),

    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['marcas-opciones'],
      })
      setNombreMarca('')
      toast.success('Marca creada.')
    },

    onError: (error) =>
      toast.error(
        error instanceof ApiError
          ? error.detalle
          : 'No se pudo crear la marca.'
      ),
  })

  const eliminarReferencia = useMutation({
    mutationFn: () =>
      gestion?.tipo === 'categoria'
        ? catalogoApi.eliminarCategoria(gestion.registro.id)
        : catalogoApi.eliminarMarca(gestion?.registro.id ?? 0),

    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [
          gestion?.tipo === 'categoria'
            ? 'categorias-opciones'
            : 'marcas-opciones',
        ],
      })

      setGestion(null)
      toast.success('Registro eliminado.')
    },

    onError: (error) =>
      toast.error(
        error instanceof ApiError
          ? error.detalle
          : 'No se pudo eliminar. Puede tener productos asociados.'
      ),
  })

  const actualizarReferencia = useMutation({
    mutationFn: () => {
      if (!editarReferencia) {
        throw new Error('Selecciona un registro para editar.')
      }

      const datos = {
        nombre: nombreReferencia.trim(),
        descripcion: editarReferencia.registro.descripcion,
        activo: editarReferencia.registro.activo,
      }

      return editarReferencia.tipo === 'categoria'
        ? catalogoApi.actualizarCategoria(
            editarReferencia.registro.id,
            datos
          )
        : catalogoApi.actualizarMarca(
            editarReferencia.registro.id,
            datos
          )
    },

    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [
          editarReferencia?.tipo === 'categoria'
            ? 'categorias-opciones'
            : 'marcas-opciones',
        ],
      })

      setEditarReferencia(null)
      toast.success('Registro actualizado.')
    },

    onError: (error) =>
      toast.error(
        error instanceof ApiError
          ? error.detalle
          : 'No se pudo actualizar.'
      ),
  })

  const cambiar = (campo: string, valor: string) =>
    setParams((actuales) => {
      const next = new URLSearchParams(actuales)

      if (valor) {
        next.set(campo, valor)
      } else {
        next.delete(campo)
      }

      next.delete('page')
      return next
    })

  const abrir = (producto: Producto | 'nuevo') => {
    setEditar(producto)

    setForm(
      producto === 'nuevo'
        ? inicial
        : {
            codigo: producto.codigo,
            nombre: producto.nombre,
            categoriaId: String(producto.categoriaId),
            marcaId: producto.marcaId
              ? String(producto.marcaId)
              : '',
            precioCompra: String(producto.precioCompra),
            precioVenta: String(producto.precioVenta),
            stockMinimo: String(producto.stockMinimo),
            unidad: producto.unidad,
            imagenUrl: producto.imagenUrl ?? '',
            descripcion: producto.descripcion ?? '',

            especificaciones: Object.entries(
              producto.especificaciones ?? {}
            ).map(([caracteristica, detalle]) => ({
              caracteristica,
              detalle:
                typeof detalle === 'string'
                  ? detalle
                  : JSON.stringify(detalle),
            })),

            compatibilidadIds: producto.compatibilidadIds,
            activo: producto.activo,
          }
    )
  }

  if (
    [productos, categorias, marcas, modelos].some(
      (consulta) => consulta.isPending
    )
  ) {
    return <Skeleton />
  }

  const fallo = [productos, categorias, marcas, modelos].find(
    (consulta) => consulta.isError
  )

  if (fallo?.isError) {
    return (
      <ErrorState
        mensaje={
          fallo.error instanceof ApiError
            ? fallo.error.detalle
            : 'No se pudo cargar el catálogo.'
        }
        reintentar={() => {
          void productos.refetch()
          void categorias.refetch()
          void marcas.refetch()
          void modelos.refetch()
        }}
      />
    )
  }

  if (
    !productos.data ||
    !categorias.data ||
    !marcas.data ||
    !modelos.data
  ) {
    return <Skeleton />
  }

  const dataProductos = productos.data
  const dataCategorias = categorias.data
  const dataMarcas = marcas.data
  const dataModelos = modelos.data

  const guardarProducto = (event: FormEvent) => {
    event.preventDefault()

    if (
      !form.codigo.trim() ||
      !form.nombre.trim() ||
      !form.categoriaId ||
      Number(form.precioCompra) < 0 ||
      Number(form.precioVenta) < 0 ||
      Number(form.stockMinimo) < 0 ||
      !form.unidad.trim()
    ) {
      toast.error(
        'Completa los campos obligatorios con valores válidos.'
      )
      return
    }

    if (
      form.especificaciones.some(
        ({ caracteristica, detalle }) =>
          !caracteristica.trim() || !detalle.trim()
      )
    ) {
      toast.error(
        'Completa la característica y su detalle, o quita la fila vacía.'
      )
      return
    }

    const nombresEspecificaciones = form.especificaciones.map(
      ({ caracteristica }) =>
        caracteristica.trim().toLocaleLowerCase()
    )

    if (
      new Set(nombresEspecificaciones).size !==
      nombresEspecificaciones.length
    ) {
      toast.error(
        'No repitas el nombre de una característica.'
      )
      return
    }

    const especificaciones = Object.fromEntries(
      form.especificaciones.map(
        ({ caracteristica, detalle }) => [
          caracteristica.trim(),
          detalle.trim(),
        ]
      )
    )

    guardar.mutate({
      codigo: form.codigo.trim(),
      nombre: form.nombre.trim(),
      categoriaId: Number(form.categoriaId),
      marcaId: form.marcaId
        ? Number(form.marcaId)
        : null,
      precioCompra: Number(form.precioCompra),
      precioVenta: Number(form.precioVenta),
      stockMinimo: Number(form.stockMinimo),
      unidad: form.unidad.trim(),
      activo: form.activo,
      imagenUrl: form.imagenUrl.trim() || null,
      descripcion: form.descripcion.trim() || null,
      especificaciones,
      compatibilidadIds: form.compatibilidadIds,
    })
  }

  return (
    <section>
      <div className="card">
        <div className="section-title">
          <div>
            <h3>Catálogo de vehículos y productos</h3>
            <span>
              Organiza el inventario por segmento, tipo, modelo y cilindrada.
            </span>
          </div>

          {puedeEditar && (
            <button
              className="btn primary"
              onClick={() => abrir('nuevo')}
            >
              Nuevo producto
            </button>
          )}
        </div>

        <div className="filter-bar">
          <SearchInput
            value={params.get('q') ?? ''}
            onChange={(value) => cambiar('q', value)}
            placeholder="Buscar código o nombre..."
          />

          <select
            aria-label="Segmento"
            value={params.get('categoriaId') ?? ''}
            onChange={(event) =>
              cambiar('categoriaId', event.target.value)
            }
          >
            <option value="">Todos los segmentos</option>
            {dataCategorias.contenido.map((item) => (
              <option key={item.id} value={item.id}>
                {item.nombre}
              </option>
            ))}
          </select>

          <select
            aria-label="Marca"
            value={params.get('marcaId') ?? ''}
            onChange={(event) =>
              cambiar('marcaId', event.target.value)
            }
          >
            <option value="">Todas las marcas</option>
            {dataMarcas.contenido.map((item) => (
              <option key={item.id} value={item.id}>
                {item.nombre}
              </option>
            ))}
          </select>

          <select
            aria-label="Estado"
            value={params.get('activo') ?? ''}
            onChange={(event) =>
              cambiar('activo', event.target.value)
            }
          >
            <option value="">Todos los estados</option>
            <option value="true">Activo</option>
            <option value="false">Inactivo</option>
          </select>

          <label>
            <input
              type="checkbox"
              checked={params.get('stockBajo') === 'true'}
              onChange={(event) =>
                cambiar(
                  'stockBajo',
                  event.target.checked ? 'true' : ''
                )
              }
            />
            Stock bajo
          </label>
        </div>

        {dataProductos.contenido.length === 0 ? (
          <p className="empty-state">
            No se encontraron productos.
          </p>
        ) : (
          <div className="scroll">
            <table>
              <thead>
                <tr>
                  <th>Imagen</th>
                  <th>Código</th>
                  <th>Producto</th>
                  <th>Segmento</th>
                  <th>Marca</th>
                  <th>Stock</th>
                  <th>Mínimo</th>
                  <th>Almacenes</th>
                  <th>Compatibilidad</th>
                  <th>Especificaciones</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>

              <tbody>
                {dataProductos.contenido.map((producto) => (
                  <tr key={producto.id}>
                    <td>
                      {producto.imagenUrl ? (
                        <img
                          className="product-thumbnail"
                          src={producto.imagenUrl}
                          alt={producto.nombre}
                          loading="lazy"
                        />
                      ) : (
                        '—'
                      )}
                    </td>

                    <td>{producto.codigo}</td>
                    <td>{producto.nombre}</td>
                    <td>{producto.categoria}</td>
                    <td>{producto.marca ?? '—'}</td>

                    <td>
                      {producto.stockTotal.toLocaleString('es-PE')}
                    </td>

                    <td>
                      {producto.stockMinimo.toLocaleString('es-PE')}
                    </td>

                    <td>
                      {producto.stockPorAlmacen
                        .map(
                          (stock) =>
                            `${stock.almacen ?? 'Almacén'}: ${stock.cantidad}`
                        )
                        .join(' · ') || '—'}
                    </td>

                    <td>
                      {producto.compatibilidades
                        .map(
                          (modelo) =>
                            `${modelo.marca} ${modelo.nombre}`
                        )
                        .join(', ') || '—'}
                    </td>

                    <td>
                      {Object.entries(producto.especificaciones)
                        .map(
                          ([clave, valor]) =>
                            `${clave}: ${String(valor)}`
                        )
                        .join(' · ') || '—'}
                    </td>

                    <td>
                      <Badge>
                        {producto.activo ? 'Activo' : 'Inactivo'}
                      </Badge>
                    </td>

                    <td>
                      {puedeEditar && (
                        <>
                          <button
                            className="btn ghost"
                            onClick={() => abrir(producto)}
                          >
                            Editar
                          </button>

                          <button
                            className="btn danger"
                            onClick={() => setEliminar(producto)}
                          >
                            Eliminar
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Pagination
          page={dataProductos.numero}
          totalPages={dataProductos.totalPaginas}
          onPage={(siguiente) =>
            setParams((actuales) => {
              const next = new URLSearchParams(actuales)
              next.set('page', String(siguiente))
              return next
            })
          }
        />

        <small className="table-total">
          {dataProductos.totalElementos} productos
        </small>
      </div>

      {puedeEditar && (
        <div className="settings-grid section-space">
          <section className="card">
            <h3>Segmentos</h3>

            <form
              className="inline-form"
              onSubmit={(event) => {
                event.preventDefault()
                if (nombreCategoria.trim()) {
                  crearCategoria.mutate()
                }
              }}
            >
              <input
                required
                value={nombreCategoria}
                onChange={(event) =>
                  setNombreCategoria(event.target.value)
                }
                placeholder="Nuevo segmento (ej. LINEALES)"
              />

              <button
                className="btn secondary"
                disabled={crearCategoria.isPending}
              >
                Crear
              </button>
            </form>

            <div className="tag-list">
              {dataCategorias.contenido.map((categoria) => (
                <span key={categoria.id}>
                  {categoria.nombre}

                  <button
                    aria-label={`Editar categoría ${categoria.nombre}`}
                    onClick={() => {
                      setEditarReferencia({
                        tipo: 'categoria',
                        registro: categoria,
                      })
                      setNombreReferencia(categoria.nombre)
                    }}
                  >
                    Editar
                  </button>

                  <button
                    aria-label={`Eliminar categoría ${categoria.nombre}`}
                    onClick={() =>
                      setGestion({
                        tipo: 'categoria',
                        registro: categoria,
                      })
                    }
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          </section>

          <section className="card">
            <h3>Marcas</h3>

            <form
              className="inline-form"
              onSubmit={(event) => {
                event.preventDefault()
                if (nombreMarca.trim()) {
                  crearMarca.mutate()
                }
              }}
            >
              <input
                required
                value={nombreMarca}
                onChange={(event) =>
                  setNombreMarca(event.target.value)
                }
                placeholder="Nueva marca"
              />

              <button
                className="btn secondary"
                disabled={crearMarca.isPending}
              >
                Crear
              </button>
            </form>

            <div className="tag-list">
              {dataMarcas.contenido.map((marca) => (
                <span key={marca.id}>
                  {marca.nombre}

                  <button
                    aria-label={`Editar marca ${marca.nombre}`}
                    onClick={() => {
                      setEditarReferencia({
                        tipo: 'marca',
                        registro: marca,
                      })
                      setNombreReferencia(marca.nombre)
                    }}
                  >
                    Editar
                  </button>

                  <button
                    aria-label={`Eliminar marca ${marca.nombre}`}
                    onClick={() =>
                      setGestion({
                        tipo: 'marca',
                        registro: marca,
                      })
                    }
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          </section>
        </div>
      )}

      {editar && (
        <FormModal
          title={
            editar === 'nuevo'
              ? 'Crear producto'
              : `Editar ${editar.codigo}`
          }
          onClose={() => setEditar(null)}
          modalClassName="product-modal"
        >
          <form
            className="form-grid product-form"
            onSubmit={guardarProducto}
          >
            <label>
              Código
              <input
                required
                maxLength={60}
                value={form.codigo}
                onChange={(event) =>
                  setForm({ ...form, codigo: event.target.value })
                }
              />
            </label>

            <label>
              Nombre
              <input
                required
                maxLength={180}
                value={form.nombre}
                onChange={(event) =>
                  setForm({ ...form, nombre: event.target.value })
                }
              />
            </label>

            <label>
              Segmento
              <select
                required
                value={form.categoriaId}
                onChange={(event) =>
                  setForm({
                    ...form,
                    categoriaId: event.target.value,
                  })
                }
              >
                <option value="">Selecciona segmento</option>

                {dataCategorias.contenido.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.nombre}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Marca
              <select
                value={form.marcaId}
                onChange={(event) =>
                  setForm({ ...form, marcaId: event.target.value })
                }
              >
                <option value="">Sin marca</option>

                {dataMarcas.contenido.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.nombre}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Precio de entrada / compra (S/)
              <input
                required
                type="number"
                min="0"
                step="0.01"
                value={form.precioCompra}
                onChange={(event) =>
                  setForm({
                    ...form,
                    precioCompra: event.target.value,
                  })
                }
              />
            </label>

            <label>
              Precio aproximado de venta (S/)
              <input
                required
                type="number"
                min="0"
                step="0.01"
                value={form.precioVenta}
                onChange={(event) =>
                  setForm({
                    ...form,
                    precioVenta: event.target.value,
                  })
                }
              />
            </label>

            <label>
              Stock mínimo
              <input
                required
                type="number"
                min="0"
                step="0.001"
                value={form.stockMinimo}
                onChange={(event) =>
                  setForm({
                    ...form,
                    stockMinimo: event.target.value,
                  })
                }
              />
            </label>

            <label>
              Unidad
              <input
                required
                maxLength={30}
                value={form.unidad}
                onChange={(event) =>
                  setForm({ ...form, unidad: event.target.value })
                }
              />
            </label>

            <label>
              Imagen URL
              <input
                type="url"
                value={form.imagenUrl}
                onChange={(event) =>
                  setForm({ ...form, imagenUrl: event.target.value })
                }
              />
            </label>

            <label>
              Descripción
              <textarea
                value={form.descripcion}
                onChange={(event) =>
                  setForm({
                    ...form,
                    descripcion: event.target.value,
                  })
                }
              />
            </label>

            <fieldset className="specifications-editor">
              <legend>Clasificación del vehículo</legend>

              <p>
                Registra aquí tipo, modelo, cilindrada y otras
                características. Ej.: Tipo = Naked; Modelo = Mamba 300;
                Cilindrada = 300 cc.
              </p>

              {form.especificaciones.map(
                (especificacion, index) => (
                  <div
                    className="specification-row"
                    key={index}
                  >
                    <label>
                      Característica
                      <input
                        value={especificacion.caracteristica}
                        placeholder="Ej. Tipo, Modelo o Cilindrada"
                        onChange={(event) =>
                          setForm({
                            ...form,
                            especificaciones:
                              form.especificaciones.map(
                                (item, fila) =>
                                  fila === index
                                    ? {
                                        ...item,
                                        caracteristica:
                                          event.target.value,
                                      }
                                    : item
                              ),
                          })
                        }
                      />
                    </label>

                    <label>
                      Detalle
                      <input
                        value={especificacion.detalle}
                        placeholder="Ej. Naked, Mamba 300, 300 cc"
                        onChange={(event) =>
                          setForm({
                            ...form,
                            especificaciones:
                              form.especificaciones.map(
                                (item, fila) =>
                                  fila === index
                                    ? {
                                        ...item,
                                        detalle: event.target.value,
                                      }
                                    : item
                              ),
                          })
                        }
                      />
                    </label>

                    <button
                      type="button"
                      className="btn ghost"
                      aria-label="Quitar característica"
                      onClick={() =>
                        setForm({
                          ...form,
                          especificaciones:
                            form.especificaciones.filter(
                              (_, fila) => fila !== index
                            ),
                        })
                      }
                    >
                      Quitar
                    </button>
                  </div>
                )
              )}

              <button
                type="button"
                className="btn secondary specification-add"
                onClick={() =>
                  setForm({
                    ...form,
                    especificaciones: [
                      ...form.especificaciones,
                      {
                        caracteristica: '',
                        detalle: '',
                      },
                    ],
                  })
                }
              >
                Añadir característica
              </button>
            </fieldset>

            <fieldset className="compatibility-fieldset">
              <legend>Compatibilidad con modelos</legend>

              {dataModelos.contenido.map((modelo) => (
                <label key={modelo.id}>
                  <input
                    type="checkbox"
                    checked={form.compatibilidadIds.includes(
                      modelo.id
                    )}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        compatibilidadIds: event.target.checked
                          ? [
                              ...form.compatibilidadIds,
                              modelo.id,
                            ]
                          : form.compatibilidadIds.filter(
                              (id) => id !== modelo.id
                            ),
                      })
                    }
                  />

                  {modelo.marca} {modelo.nombre}{' '}
                  {modelo.anioInicio ?? ''}
                  {modelo.anioFin
                    ? `–${modelo.anioFin}`
                    : ''}
                </label>
              ))}
            </fieldset>

            <label className="product-active">
              <input
                type="checkbox"
                checked={form.activo}
                onChange={(event) =>
                  setForm({
                    ...form,
                    activo: event.target.checked,
                  })
                }
              />
              Activo
            </label>

            <div className="modal-actions">
              <button
                type="button"
                className="btn secondary"
                onClick={() => setEditar(null)}
              >
                Cancelar
              </button>

              <button
                className="btn primary"
                disabled={guardar.isPending}
              >
                {guardar.isPending
                  ? 'Guardando…'
                  : 'Guardar producto'}
              </button>
            </div>
          </form>
        </FormModal>
      )}

      {eliminar && (
        <ConfirmDialog
          title="Eliminar producto"
          cancelar={() => setEliminar(null)}
          confirmar={() => borrar.mutate(eliminar)}
        >
          Se eliminará {eliminar.codigo} · {eliminar.nombre}.
        </ConfirmDialog>
      )}

      {gestion && (
        <ConfirmDialog
          title={`Eliminar ${gestion.tipo}`}
          cancelar={() => setGestion(null)}
          confirmar={() => eliminarReferencia.mutate()}
        >
          {gestion.registro.nombre} se eliminará si no tiene productos asociados.
        </ConfirmDialog>
      )}

      {editarReferencia && (
        <FormModal
          title={`Editar ${editarReferencia.tipo}`}
          onClose={() => setEditarReferencia(null)}
        >
          <form
            className="form-grid"
            onSubmit={(event) => {
              event.preventDefault()
              if (nombreReferencia.trim()) {
                actualizarReferencia.mutate()
              }
            }}
          >
            <label>
              Nombre
              <input
                required
                maxLength={120}
                value={nombreReferencia}
                onChange={(event) =>
                  setNombreReferencia(event.target.value)
                }
              />
            </label>

            <div className="modal-actions">
              <button
                type="button"
                className="btn secondary"
                onClick={() => setEditarReferencia(null)}
              >
                Cancelar
              </button>

              <button
                className="btn primary"
                disabled={
                  !nombreReferencia.trim() ||
                  actualizarReferencia.isPending
                }
              >
                Guardar
              </button>
            </div>
          </form>
        </FormModal>
      )}
    </section>
  )
}
