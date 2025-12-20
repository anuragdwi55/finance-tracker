// src/charts.ts
import {
  Chart as ChartJS,
  ArcElement, BarElement, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend, Filler
} from 'chart.js'

ChartJS.register(
  ArcElement, BarElement, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend, Filler
)

try {
  const css = getComputedStyle(document.documentElement)
  const text = (css.getPropertyValue('--text') || '#1f2937').trim()
  ChartJS.defaults.color = text
  ChartJS.defaults.borderColor = 'rgba(31, 41, 55, 0.12)'
  ChartJS.defaults.font.family =
    'Inter, ui-sans-serif, system-ui, Segoe UI, Roboto, Helvetica, Arial'
} catch {}

export default ChartJS
