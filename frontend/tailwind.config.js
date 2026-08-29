/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#070b14',
        panel: '#101827',
        line: '#1f2a3d',
      },
    },
  },
  plugins: [],
}
