import type { Config } from 'tailwindcss';

export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      colors: {
        brand: {
          blue: '#2F6BFF',
          purple: '#A93BFF',
          pink: '#FF2E7A',
        },
        ui: {
          bg: '#F8F9FB',
          card: '#FFFFFF',
          border: '#E5E7EB',
          text: {
            primary: '#111827',
            secondary: '#6B7280',
            muted: '#9CA3AF',
          },
        },
        platform: {
          facebook: '#3B82F6',
          intramuros: '#A78BFA',
        },
        status: {
          scheduled: '#60A5FA',
          pending: '#FBBF24',
          success: '#34D399',
          danger: '#F87171',
        },
      },
      backgroundImage: {
        'brand-gradient': 'linear-gradient(90deg, #2F6BFF 0%, #A93BFF 50%, #FF2E7A 100%)',
      },
      borderRadius: {
        sm: '8px',
        md: '12px',
        lg: '16px',
        xl: '20px',
      },
      boxShadow: {
        card: '0 1px 3px rgba(0,0,0,0.06)',
        hover: '0 4px 12px rgba(0,0,0,0.08)',
      },
      fontSize: {
        'display-1': ['28px', { lineHeight: '36px', fontWeight: '700' }],
        'heading-2': ['20px', { lineHeight: '28px', fontWeight: '600' }],
        'heading-3': ['16px', { lineHeight: '24px', fontWeight: '600' }],
        body: ['14px', { lineHeight: '20px', fontWeight: '400' }],
        label: [
          '12px',
          { lineHeight: '16px', fontWeight: '500', letterSpacing: '0.2px' },
        ],
        button: ['14px', { lineHeight: '20px', fontWeight: '600' }],
      },
      spacing: {
        18: '4.5rem',
        22: '5.5rem',
      },
    },
  },
  plugins: [],
} satisfies Config;