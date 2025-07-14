// frontend/postcss.config.js
import tailwindcss from '@tailwindcss/postcss'
import autoprefixer from 'autoprefixer'

export default {
  plugins: [
    tailwindcss(),
    autoprefixer(),
  ],
}
// This configuration file sets up PostCSS to use Tailwind CSS and Autoprefixer.
// Tailwind CSS is a utility-first CSS framework, and Autoprefixer adds vendor prefixes