import { useState, type ReactNode } from 'react'
import { keepPreviousData, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'sonner'
import { AuthProvider } from '../auth/AuthContext'

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: { retry: 1, refetchOnWindowFocus: false, placeholderData: keepPreviousData },
    },
  }))
  return <QueryClientProvider client={queryClient}>
    <BrowserRouter>
      <AuthProvider>
        {children}
        <Toaster position="top-right" richColors />
      </AuthProvider>
    </BrowserRouter>
  </QueryClientProvider>
}
