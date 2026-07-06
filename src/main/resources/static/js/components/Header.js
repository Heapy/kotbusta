import { h } from 'preact';

function navClass(view, currentView, extra = '') {
  return `nav-button ${currentView === view ? 'active' : ''} ${extra}`.trim();
}

export function Header({ user, onNavigate, currentView, isAdmin }) {
  return h('header', { className: 'site-header' },
    h('div', { className: 'brand' },
      h('span', { className: 'brand-mark', 'aria-hidden': 'true' }, 'K'),
      h('h1', { className: 'brand-title' }, 'Kotbusta')
    ),
    h('nav', { className: 'top-nav', 'aria-label': 'Primary navigation' },
      h('button', {
        className: navClass('books', currentView),
        onClick: () => onNavigate('books')
      }, 'Books'),
      h('button', {
        className: navClass('kindle', currentView),
        onClick: () => onNavigate('kindle')
      }, 'Kindle'),
      isAdmin && h('button', {
        className: navClass('admin', currentView, 'admin'),
        onClick: () => onNavigate('admin')
      }, 'Admin')
    ),
    h('div', { className: 'user-menu' },
      user && h('div', { className: 'user-identity' },
        user.data.avatarUrl && h('img', {
          className: 'avatar',
          src: user.data.avatarUrl,
          alt: user.data.name
        }),
        h('span', null, user.data.name)
      ),
      h('a', { href: '/logout', className: 'logout-link' }, 'Logout')
    )
  );
}
