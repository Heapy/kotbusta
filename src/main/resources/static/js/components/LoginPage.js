import { h } from 'preact';

export function LoginPage() {
  return h('main', { className: 'auth-page' },
    h('section', { className: 'auth-card' },
      h('div', { className: 'auth-mark', 'aria-hidden': 'true' }, 'K'),
      h('h1', { className: 'auth-title' }, 'Kotbusta'),
      h('p', { className: 'auth-copy' }, 'Sign in to browse, download, and send books to Kindle.'),
      h('a', {
        href: '/login',
        className: 'button primary full'
      }, 'Continue with Google'),
      h('p', { className: 'auth-footnote' }, 'Access is managed by the administrator.')
    )
  );
}
