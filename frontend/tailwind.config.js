// tailwind.config.js — プロジェクトルートに配置
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}"
  ],
  darkMode: 'media', // media query strategy
  theme: {
    extend: {},
  },
  plugins: [],
}
