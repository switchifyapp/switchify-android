import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://android.theswitchify.app',
  integrations: [
    starlight({
      title: 'Switchify',
      description: 'Switch access for Android. How to set up Switchify, connect a switch, scan, use the menu, and control a PC from your phone.',
      social: [
        { icon: 'github', label: 'GitHub', href: 'https://github.com/switchifyapp/switchify-android' },
      ],
      editLink: {
        baseUrl: 'https://github.com/switchifyapp/switchify-android/edit/main/site/',
      },
      lastUpdated: true,
      customCss: ['./src/styles/custom.css'],
      sidebar: [
        { label: 'Overview', slug: 'index' },
        {
          label: 'Get started',
          items: [
            { label: 'Install', slug: 'get-started/install' },
            { label: 'Connect a switch', slug: 'get-started/connect-a-switch' },
          ],
        },
        {
          label: 'Using your device',
          items: [
            { label: 'Scanning', slug: 'using/scanning' },
            { label: 'The menu', slug: 'using/the-menu' },
            { label: 'Keyboard', slug: 'using/keyboard' },
            { label: 'Gestures and actions', slug: 'using/gestures-and-actions' },
            { label: 'Settings', slug: 'using/settings' },
          ],
        },
        {
          label: 'PC control',
          items: [
            { label: 'Pair a PC', slug: 'pc-control/pairing' },
            { label: 'Mouse, Typing and Window', slug: 'pc-control/mouse-typing-window' },
            { label: 'Switch Forwarding', slug: 'pc-control/switch-forwarding' },
          ],
        },
        { label: 'Pro and your account', slug: 'pro-and-account' },
        { label: 'Privacy', slug: 'privacy' },
        { label: 'Troubleshooting', slug: 'troubleshooting' },
        { label: 'Switchify PC docs', link: 'https://pc.theswitchify.app' },
      ],
    }),
  ],
});
