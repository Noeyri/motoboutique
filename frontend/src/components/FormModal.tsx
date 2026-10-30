import type { ReactNode } from 'react'
export function FormModal({ title, children, onClose, modalClassName }: {
  title: string; children: ReactNode; onClose: () => void; modalClassName?: string
}) {
  return <div className="overlay" role="dialog" aria-modal="true"><div className={`modal ${modalClassName ?? ''}`}>
    <div className="modal-title"><h2>{title}</h2><button className="icon-btn" onClick={onClose}>Cerrar</button></div>{children}
  </div></div>
}
