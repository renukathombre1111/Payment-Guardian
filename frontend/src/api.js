import axios from 'axios'

export const api = axios.create({ baseURL: '/api' })

export function inr(n) {
  if (n == null) return '₹0'
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(n)
}
