import { h, render } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import htm from 'https://esm.sh/htm@3.1.1';

const html = htm.bind(h);

// API Helper
const api = {
  async get(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  },
  async post(url, data) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error(await res.text());
    return res.status === 204 ? null : res.json();
  },
  async put(url, data) {
    const res = await fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  },
  async delete(url) {
    const res = await fetch(url, { method: 'DELETE' });
    if (!res.ok) throw new Error(await res.text());
    return res.status === 204 ? null : res.json();
  }
};

// Header Component
function Header({ user, onNavigate, currentView, isAdmin }) {
  return html`
    <header style=${{
      background: '#2c3e50',
      color: 'white',
      padding: '1rem 2rem',
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <div>
        <h1 style=${{ margin: 0, fontSize: '1.5rem' }}>📚 Kotbusta</h1>
      </div>
      <nav style=${{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
        <button onClick=${() => onNavigate('books')}
          style=${{ background: currentView === 'books' ? '#34495e' : 'transparent',
                    border: 'none', color: 'white', padding: '0.5rem 1rem',
                    cursor: 'pointer', borderRadius: '4px' }}>
          Books
        </button>
        <button onClick=${() => onNavigate('starred')}
          style=${{ background: currentView === 'starred' ? '#34495e' : 'transparent',
                    border: 'none', color: 'white', padding: '0.5rem 1rem',
                    cursor: 'pointer', borderRadius: '4px' }}>
          ⭐ Starred
        </button>
        <button onClick=${() => onNavigate('activity')}
          style=${{ background: currentView === 'activity' ? '#34495e' : 'transparent',
                    border: 'none', color: 'white', padding: '0.5rem 1rem',
                    cursor: 'pointer', borderRadius: '4px' }}>
          Activity
        </button>
        <button onClick=${() => onNavigate('kindle')}
          style=${{ background: currentView === 'kindle' ? '#34495e' : 'transparent',
                    border: 'none', color: 'white', padding: '0.5rem 1rem',
                    cursor: 'pointer', borderRadius: '4px' }}>
          Kindle
        </button>
        ${isAdmin && html`
          <button onClick=${() => onNavigate('admin')}
            style=${{ background: currentView === 'admin' ? '#e74c3c' : '#c0392b',
                      border: 'none', color: 'white', padding: '0.5rem 1rem',
                      cursor: 'pointer', borderRadius: '4px' }}>
            Admin
          </button>
        `}
      </nav>
      <div style=${{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        ${user && html`
          <div style=${{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            ${user.data.avatarUrl && html`
              <img src=${user.data.avatarUrl} alt=${user.data.name}
                style=${{ width: '32px', height: '32px', borderRadius: '50%' }} />
            `}
            <span>${user.data.name}</span>
          </div>
        `}
        <a href="/logout" style=${{ color: 'white', textDecoration: 'none' }}>Logout</a>
      </div>
    </header>
  `;
}

// Search Bar Component
function SearchBar({ onSearch, initialQuery = '' }) {
  const [query, setQuery] = useState(initialQuery);
  const [filters, setFilters] = useState({ genre: '', language: '', author: '' });
  const [showFilters, setShowFilters] = useState(false);

  const handleSearch = (e) => {
    e.preventDefault();
    onSearch({ q: query, ...filters });
  };

  return html`
    <div style=${{ marginBottom: '2rem' }}>
      <form onSubmit=${handleSearch} style=${{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <input
          type="text"
          value=${query}
          onInput=${(e) => setQuery(e.target.value)}
          placeholder="Search books..."
          style=${{
            flex: 1,
            padding: '0.75rem',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '1rem'
          }}
        />
        <button type="button" onClick=${() => setShowFilters(!showFilters)}
          style=${{
            padding: '0.75rem 1rem',
            background: '#95a5a6',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}>
          Filters
        </button>
        <button type="submit"
          style=${{
            padding: '0.75rem 1.5rem',
            background: '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}>
          Search
        </button>
      </form>

      ${showFilters && html`
        <div style=${{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1rem',
          padding: '1rem',
          background: '#ecf0f1',
          borderRadius: '4px'
        }}>
          <input
            type="text"
            value=${filters.genre}
            onInput=${(e) => setFilters({ ...filters, genre: e.target.value })}
            placeholder="Genre"
            style=${{ padding: '0.5rem', border: '1px solid #bdc3c7', borderRadius: '4px' }}
          />
          <input
            type="text"
            value=${filters.language}
            onInput=${(e) => setFilters({ ...filters, language: e.target.value })}
            placeholder="Language"
            style=${{ padding: '0.5rem', border: '1px solid #bdc3c7', borderRadius: '4px' }}
          />
          <input
            type="text"
            value=${filters.author}
            onInput=${(e) => setFilters({ ...filters, author: e.target.value })}
            placeholder="Author"
            style=${{ padding: '0.5rem', border: '1px solid #bdc3c7', borderRadius: '4px' }}
          />
        </div>
      `}
    </div>
  `;
}

// Book Card Component
function BookCard({ book, onSelect, onToggleStar }) {
  return html`
    <div style=${{
      border: '1px solid #ddd',
      borderRadius: '8px',
      padding: '1rem',
      background: 'white',
      boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
      cursor: 'pointer',
      transition: 'transform 0.2s, box-shadow 0.2s',
      position: 'relative'
    }}
    onMouseOver=${(e) => {
      e.currentTarget.style.transform = 'translateY(-2px)';
      e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.1)';
    }}
    onMouseOut=${(e) => {
      e.currentTarget.style.transform = 'translateY(0)';
      e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.05)';
    }}>
      <button
        onClick=${(e) => {
          e.stopPropagation();
          onToggleStar(book.id, book.isStarred);
        }}
        style=${{
          position: 'absolute',
          top: '0.5rem',
          right: '0.5rem',
          background: 'none',
          border: 'none',
          fontSize: '1.5rem',
          cursor: 'pointer'
        }}
      >
        ${book.isStarred ? '⭐' : '☆'}
      </button>

      <div onClick=${() => onSelect(book.id)}>
        ${book.coverImageUrl && html`
          <img src=${book.coverImageUrl} alt=${book.title}
            style=${{
              width: '100%',
              height: '200px',
              objectFit: 'cover',
              borderRadius: '4px',
              marginBottom: '0.75rem'
            }}
          />
        `}
        <h3 style=${{ margin: '0 0 0.5rem 0', fontSize: '1rem' }}>${book.title}</h3>
        <p style=${{ margin: '0 0 0.25rem 0', color: '#7f8c8d', fontSize: '0.875rem' }}>
          ${book.authors.join(', ')}
        </p>
        ${book.series && html`
          <p style=${{ margin: '0 0 0.25rem 0', color: '#95a5a6', fontSize: '0.75rem' }}>
            ${book.series}${book.seriesNumber ? ` #${book.seriesNumber}` : ''}
          </p>
        `}
        <div style=${{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
          ${book.genre && html`
            <span style=${{
              background: '#ecf0f1',
              padding: '0.25rem 0.5rem',
              borderRadius: '4px',
              fontSize: '0.75rem',
              color: '#34495e'
            }}>
              ${book.genre}
            </span>
          `}
          <span style=${{
            background: '#e8f4f8',
            padding: '0.25rem 0.5rem',
            borderRadius: '4px',
            fontSize: '0.75rem',
            color: '#2980b9'
          }}>
            ${book.language}
          </span>
        </div>
      </div>
    </div>
  `;
}

// Book Detail Component
function BookDetail({ bookId, onBack, onRefresh }) {
  const [book, setBook] = useState(null);
  const [comments, setComments] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [note, setNote] = useState('');
  const [notePrivate, setNotePrivate] = useState(true);
  const [loading, setLoading] = useState(true);
  const [sendingToKindle, setSendingToKindle] = useState(false);

  useEffect(() => {
    loadBook();
    loadComments();
  }, [bookId]);

  const loadBook = async () => {
    try {
      const res = await api.get(`/api/books/${bookId}`);
      setBook(res.data);
      setNote(res.data.userNote || '');
    } catch (err) {
      console.error('Failed to load book:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadComments = async () => {
    try {
      const res = await api.get(`/api/books/${bookId}/comments?limit=50`);
      setComments(res.data || []);
    } catch (err) {
      console.error('Failed to load comments:', err);
    }
  };

  const toggleStar = async () => {
    try {
      if (book.isStarred) {
        await api.delete(`/api/books/${bookId}/star`);
      } else {
        await api.post(`/api/books/${bookId}/star`);
      }
      await loadBook();
      onRefresh();
    } catch (err) {
      alert('Failed to toggle star: ' + err.message);
    }
  };

  const addComment = async (e) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    try {
      await api.post(`/api/books/${bookId}/comments`, { comment: newComment });
      setNewComment('');
      await loadComments();
    } catch (err) {
      alert('Failed to add comment: ' + err.message);
    }
  };

  const deleteComment = async (commentId) => {
    if (!confirm('Delete this comment?')) return;
    try {
      await api.delete(`/api/comments/${commentId}`);
      await loadComments();
    } catch (err) {
      alert('Failed to delete comment: ' + err.message);
    }
  };

  const saveNote = async (e) => {
    e.preventDefault();
    try {
      if (note.trim()) {
        await api.post(`/api/books/${bookId}/notes`, { note, isPrivate: notePrivate });
      } else {
        await api.delete(`/api/books/${bookId}/notes`);
      }
      alert('Note saved!');
    } catch (err) {
      alert('Failed to save note: ' + err.message);
    }
  };

  const download = (format) => {
    window.open(`/api/books/${bookId}/download/${format}`, '_blank');
  };

  const sendToKindle = async (deviceId, format) => {
    try {
      setSendingToKindle(true);
      await api.post(`/api/books/${bookId}/send-to-kindle`, { deviceId, format });
      alert('Book queued for sending to Kindle!');
    } catch (err) {
      alert('Failed to send to Kindle: ' + err.message);
    } finally {
      setSendingToKindle(false);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  if (!book) {
    return html`<div style=${{ padding: '2rem' }}>Book not found</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <button onClick=${onBack} style=${{
        marginBottom: '1rem',
        padding: '0.5rem 1rem',
        background: '#95a5a6',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer'
      }}>
        ← Back
      </button>

      <div style=${{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: '2rem', marginBottom: '2rem' }}>
        <div>
          ${book.coverImageUrl && html`
            <img src=${book.coverImageUrl} alt=${book.title}
              style=${{ width: '100%', borderRadius: '8px', marginBottom: '1rem' }}
            />
          `}
          <button onClick=${toggleStar} style=${{
            width: '100%',
            padding: '0.75rem',
            background: book.isStarred ? '#f39c12' : '#ecf0f1',
            color: book.isStarred ? 'white' : '#34495e',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            marginBottom: '0.5rem',
            fontWeight: 'bold'
          }}>
            ${book.isStarred ? '⭐ Starred' : '☆ Star this book'}
          </button>

          <div style=${{ marginTop: '1rem' }}>
            <h4 style=${{ marginBottom: '0.5rem' }}>Download</h4>
            <button onClick=${() => download('fb2')} style=${{
              width: '100%',
              padding: '0.5rem',
              background: '#3498db',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              marginBottom: '0.25rem'
            }}>
              Download FB2
            </button>
            <button onClick=${() => download('epub')} style=${{
              width: '100%',
              padding: '0.5rem',
              background: '#3498db',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              marginBottom: '0.25rem'
            }}>
              Download EPUB
            </button>
            <button onClick=${() => download('mobi')} style=${{
              width: '100%',
              padding: '0.5rem',
              background: '#3498db',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}>
              Download MOBI
            </button>
          </div>
        </div>

        <div>
          <h1 style=${{ marginTop: 0 }}>${book.title}</h1>
          <p style=${{ fontSize: '1.125rem', color: '#7f8c8d', marginBottom: '1rem' }}>
            ${book.authors.map(a => a.fullName).join(', ')}
          </p>

          ${book.series && html`
            <p style=${{ color: '#95a5a6', marginBottom: '1rem' }}>
              Series: ${book.series.name}${book.seriesNumber ? ` #${book.seriesNumber}` : ''}
            </p>
          `}

          <div style=${{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
            ${book.genre && html`
              <span style=${{
                background: '#ecf0f1',
                padding: '0.5rem 1rem',
                borderRadius: '4px',
                color: '#34495e'
              }}>
                ${book.genre}
              </span>
            `}
            <span style=${{
              background: '#e8f4f8',
              padding: '0.5rem 1rem',
              borderRadius: '4px',
              color: '#2980b9'
            }}>
              ${book.language}
            </span>
          </div>

          ${book.annotation && html`
            <div style=${{
              background: '#f8f9fa',
              padding: '1rem',
              borderRadius: '8px',
              marginBottom: '2rem',
              whiteSpace: 'pre-wrap'
            }}>
              ${book.annotation}
            </div>
          `}

          <div style=${{ marginBottom: '2rem' }}>
            <h3>My Note</h3>
            <form onSubmit=${saveNote}>
              <textarea
                value=${note}
                onInput=${(e) => setNote(e.target.value)}
                placeholder="Add your personal note..."
                style=${{
                  width: '100%',
                  minHeight: '100px',
                  padding: '0.75rem',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '1rem',
                  marginBottom: '0.5rem',
                  boxSizing: 'border-box'
                }}
              />
              <div style=${{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <label style=${{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <input
                    type="checkbox"
                    checked=${notePrivate}
                    onChange=${(e) => setNotePrivate(e.target.checked)}
                  />
                  Private
                </label>
                <button type="submit" style=${{
                  padding: '0.5rem 1rem',
                  background: '#27ae60',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}>
                  Save Note
                </button>
              </div>
            </form>
          </div>

          <div>
            <h3>Comments</h3>
            <form onSubmit=${addComment} style=${{ marginBottom: '1rem' }}>
              <textarea
                value=${newComment}
                onInput=${(e) => setNewComment(e.target.value)}
                placeholder="Add a comment..."
                style=${{
                  width: '100%',
                  minHeight: '80px',
                  padding: '0.75rem',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '1rem',
                  marginBottom: '0.5rem',
                  boxSizing: 'border-box'
                }}
              />
              <button type="submit" style=${{
                padding: '0.5rem 1rem',
                background: '#3498db',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Add Comment
              </button>
            </form>

            <div style=${{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              ${comments.map(comment => html`
                <div key=${comment.id} style=${{
                  border: '1px solid #e1e8ed',
                  borderRadius: '8px',
                  padding: '1rem',
                  background: 'white'
                }}>
                  <div style=${{ display: 'flex', gap: '0.75rem', marginBottom: '0.5rem' }}>
                    ${comment.userAvatarUrl && html`
                      <img src=${comment.userAvatarUrl} alt=${comment.userName}
                        style=${{ width: '40px', height: '40px', borderRadius: '50%' }}
                      />
                    `}
                    <div style=${{ flex: 1 }}>
                      <div style=${{ fontWeight: 'bold', marginBottom: '0.25rem' }}>
                        ${comment.userName}
                      </div>
                      <div style=${{ color: '#95a5a6', fontSize: '0.875rem' }}>
                        ${new Date(comment.createdAt).toLocaleString()}
                      </div>
                    </div>
                    <button onClick=${() => deleteComment(comment.id)} style=${{
                      background: 'none',
                      border: 'none',
                      color: '#e74c3c',
                      cursor: 'pointer',
                      fontSize: '0.875rem'
                    }}>
                      Delete
                    </button>
                  </div>
                  <p style=${{ margin: '0', whiteSpace: 'pre-wrap' }}>${comment.comment}</p>
                </div>
              `)}
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

// Books List Component
function BooksList({ starred = false, onSelectBook }) {
  const [books, setBooks] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [offset, setOffset] = useState(0);
  const [searchParams, setSearchParams] = useState(null);
  const limit = 20;

  useEffect(() => {
    loadBooks();
  }, [offset, searchParams, starred]);

  const loadBooks = async () => {
    setLoading(true);
    try {
      let url;
      if (searchParams) {
        const params = new URLSearchParams({
          ...searchParams,
          limit,
          offset
        });
        url = `/api/books/search?${params}`;
      } else if (starred) {
        url = `/api/books/starred?limit=${limit}&offset=${offset}`;
      } else {
        url = `/api/books?limit=${limit}&offset=${offset}`;
      }

      const res = await api.get(url);
      setBooks(res.data.books || []);
      setTotal(res.data.total || 0);
    } catch (err) {
      console.error('Failed to load books:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (params) => {
    setSearchParams(params);
    setOffset(0);
  };

  const toggleStar = async (bookId, isStarred) => {
    try {
      if (isStarred) {
        await api.delete(`/api/books/${bookId}/star`);
      } else {
        await api.post(`/api/books/${bookId}/star`);
      }
      await loadBooks();
    } catch (err) {
      alert('Failed to toggle star: ' + err.message);
    }
  };

  const hasMore = offset + limit < total;

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2>${starred ? 'Starred Books' : 'All Books'}</h2>

      ${!starred && html`
        <${SearchBar} onSearch=${handleSearch} />
      `}

      ${loading ? html`
        <div style=${{ textAlign: 'center', padding: '2rem' }}>Loading...</div>
      ` : html`
        <div style=${{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))',
          gap: '1.5rem',
          marginBottom: '2rem'
        }}>
          ${books.map(book => html`
            <${BookCard}
              key=${book.id}
              book=${book}
              onSelect=${onSelectBook}
              onToggleStar=${toggleStar}
            />
          `)}
        </div>
      `}

      ${books.length === 0 && !loading && html`
        <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
          No books found
        </div>
      `}

      <div style=${{ display: 'flex', justifyContent: 'center', gap: '1rem', marginTop: '2rem' }}>
        <button
          onClick=${() => setOffset(Math.max(0, offset - limit))}
          disabled=${offset === 0}
          style=${{
            padding: '0.75rem 1.5rem',
            background: offset === 0 ? '#ecf0f1' : '#3498db',
            color: offset === 0 ? '#95a5a6' : 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: offset === 0 ? 'not-allowed' : 'pointer'
          }}
        >
          Previous
        </button>
        <span style=${{ padding: '0.75rem', color: '#7f8c8d' }}>
          ${offset + 1}-${Math.min(offset + limit, total)} of ${total}
        </span>
        <button
          onClick=${() => setOffset(offset + limit)}
          disabled=${!hasMore}
          style=${{
            padding: '0.75rem 1.5rem',
            background: !hasMore ? '#ecf0f1' : '#3498db',
            color: !hasMore ? '#95a5a6' : 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: !hasMore ? 'not-allowed' : 'pointer'
          }}
        >
          Next
        </button>
      </div>
    </div>
  `;
}

// Activity Component
function Activity() {
  const [activity, setActivity] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadActivity();
  }, []);

  const loadActivity = async () => {
    try {
      const res = await api.get('/api/activity?limit=50');
      setActivity(res.data);
    } catch (err) {
      console.error('Failed to load activity:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2>Recent Activity</h2>

      <div style=${{ marginBottom: '2rem' }}>
        <h3>Recent Comments</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          ${activity.comments.map(comment => html`
            <div key=${comment.id} style=${{
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              padding: '1rem',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', gap: '0.75rem', marginBottom: '0.5rem' }}>
                ${comment.userAvatarUrl && html`
                  <img src=${comment.userAvatarUrl} alt=${comment.userName}
                    style=${{ width: '40px', height: '40px', borderRadius: '50%' }}
                  />
                `}
                <div>
                  <div style=${{ fontWeight: 'bold' }}>${comment.userName}</div>
                  <div style=${{ color: '#3498db', fontSize: '0.875rem' }}>
                    on "${comment.bookTitle}"
                  </div>
                  <div style=${{ color: '#95a5a6', fontSize: '0.875rem' }}>
                    ${new Date(comment.createdAt).toLocaleString()}
                  </div>
                </div>
              </div>
              <p style=${{ margin: '0', whiteSpace: 'pre-wrap' }}>${comment.comment}</p>
            </div>
          `)}
        </div>
      </div>

      <div>
        <h3>Recent Downloads</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          ${activity.downloads.map(download => html`
            <div key=${download.id} style=${{
              padding: '0.75rem',
              border: '1px solid #e1e8ed',
              borderRadius: '4px',
              background: 'white'
            }}>
              <div style=${{ fontWeight: 'bold' }}>${download.bookTitle}</div>
              <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                ${download.format.toUpperCase()} · ${new Date(download.createdAt).toLocaleString()}
              </div>
            </div>
          `)}
        </div>
      </div>
    </div>
  `;
}

// Kindle Management Component
function KindleManagement() {
  const [devices, setDevices] = useState([]);
  const [sendHistory, setSendHistory] = useState([]);
  const [showAddDevice, setShowAddDevice] = useState(false);
  const [newDevice, setNewDevice] = useState({ email: '', name: '' });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [devicesRes, historyRes] = await Promise.all([
        api.get('/api/kindle/devices'),
        api.get('/api/kindle/sends?limit=20')
      ]);
      setDevices(devicesRes.data || []);
      setSendHistory(historyRes.data.items || []);
    } catch (err) {
      console.error('Failed to load Kindle data:', err);
    } finally {
      setLoading(false);
    }
  };

  const addDevice = async (e) => {
    e.preventDefault();
    try {
      await api.post('/api/kindle/devices', newDevice);
      setNewDevice({ email: '', name: '' });
      setShowAddDevice(false);
      await loadData();
    } catch (err) {
      alert('Failed to add device: ' + err.message);
    }
  };

  const deleteDevice = async (deviceId) => {
    if (!confirm('Delete this device?')) return;
    try {
      await api.delete(`/api/kindle/devices/${deviceId}`);
      await loadData();
    } catch (err) {
      alert('Failed to delete device: ' + err.message);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2>Kindle Management</h2>

      <div style=${{ marginBottom: '2rem' }}>
        <div style=${{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3>My Devices</h3>
          <button onClick=${() => setShowAddDevice(!showAddDevice)} style=${{
            padding: '0.5rem 1rem',
            background: '#27ae60',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}>
            + Add Device
          </button>
        </div>

        ${showAddDevice && html`
          <form onSubmit=${addDevice} style=${{
            background: '#ecf0f1',
            padding: '1rem',
            borderRadius: '8px',
            marginBottom: '1rem'
          }}>
            <input
              type="email"
              value=${newDevice.email}
              onInput=${(e) => setNewDevice({ ...newDevice, email: e.target.value })}
              placeholder="Kindle Email (e.g., user@kindle.com)"
              required
              style=${{
                width: '100%',
                padding: '0.75rem',
                border: '1px solid #bdc3c7',
                borderRadius: '4px',
                marginBottom: '0.5rem',
                boxSizing: 'border-box'
              }}
            />
            <input
              type="text"
              value=${newDevice.name}
              onInput=${(e) => setNewDevice({ ...newDevice, name: e.target.value })}
              placeholder="Device Name (e.g., My Kindle)"
              required
              style=${{
                width: '100%',
                padding: '0.75rem',
                border: '1px solid #bdc3c7',
                borderRadius: '4px',
                marginBottom: '0.5rem',
                boxSizing: 'border-box'
              }}
            />
            <div style=${{ display: 'flex', gap: '0.5rem' }}>
              <button type="submit" style=${{
                padding: '0.5rem 1rem',
                background: '#27ae60',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Add
              </button>
              <button type="button" onClick=${() => setShowAddDevice(false)} style=${{
                padding: '0.5rem 1rem',
                background: '#95a5a6',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Cancel
              </button>
            </div>
          </form>
        `}

        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          ${devices.map(device => html`
            <div key=${device.id} style=${{
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div>
                <div style=${{ fontWeight: 'bold' }}>${device.name}</div>
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>${device.email}</div>
              </div>
              <button onClick=${() => deleteDevice(device.id)} style=${{
                padding: '0.5rem 1rem',
                background: '#e74c3c',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Delete
              </button>
            </div>
          `)}
        </div>

        ${devices.length === 0 && html`
          <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
            No devices yet. Add your first Kindle device to get started!
          </div>
        `}
      </div>

      <div>
        <h3>Send History</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          ${sendHistory.map(item => html`
            <div key=${item.id} style=${{
              padding: '0.75rem',
              border: '1px solid #e1e8ed',
              borderRadius: '4px',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                <div>
                  <div style=${{ fontWeight: 'bold' }}>${item.bookTitle}</div>
                  <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                    to ${item.deviceName} · ${item.format}
                  </div>
                  <div style=${{ color: '#95a5a6', fontSize: '0.75rem' }}>
                    ${new Date(item.createdAt).toLocaleString()}
                  </div>
                </div>
                <span style=${{
                  padding: '0.25rem 0.75rem',
                  borderRadius: '4px',
                  fontSize: '0.75rem',
                  background: item.status === 'COMPLETED' ? '#d5f4e6' :
                             item.status === 'FAILED' ? '#fadbd8' :
                             item.status === 'PROCESSING' ? '#fff3cd' : '#e8f4f8',
                  color: item.status === 'COMPLETED' ? '#27ae60' :
                         item.status === 'FAILED' ? '#e74c3c' :
                         item.status === 'PROCESSING' ? '#f39c12' : '#3498db'
                }}>
                  ${item.status}
                </span>
              </div>
              ${item.lastError && html`
                <div style=${{ color: '#e74c3c', fontSize: '0.75rem', marginTop: '0.5rem' }}>
                  Error: ${item.lastError}
                </div>
              `}
            </div>
          `)}
        </div>

        ${sendHistory.length === 0 && html`
          <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
            No send history yet
          </div>
        `}
      </div>
    </div>
  `;
}

// Login Page Component
function LoginPage() {
  return html`
    <div style=${{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: '2rem'
    }}>
      <div style=${{
        background: 'white',
        borderRadius: '16px',
        padding: '3rem',
        boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
        maxWidth: '500px',
        width: '100%',
        textAlign: 'center'
      }}>
        <div style=${{ fontSize: '4rem', marginBottom: '1rem' }}>📚</div>
        <h1 style=${{
          margin: '0 0 0.5rem 0',
          fontSize: '2.5rem',
          color: '#2c3e50',
          fontWeight: '700'
        }}>
          Kotbusta
        </h1>
        <p style=${{
          margin: '0 0 2rem 0',
          fontSize: '1.125rem',
          color: '#7f8c8d',
          lineHeight: '1.6'
        }}>
          Your personal digital library
        </p>

        <div style=${{
          background: '#f8f9fa',
          borderRadius: '8px',
          padding: '1.5rem',
          marginBottom: '2rem'
        }}>
          <p style=${{ margin: '0 0 1rem 0', color: '#34495e', fontSize: '0.875rem' }}>
            Access thousands of books, manage your reading list, and sync to your Kindle device.
          </p>
          <ul style=${{
            listStyle: 'none',
            padding: 0,
            margin: 0,
            textAlign: 'left',
            color: '#7f8c8d',
            fontSize: '0.875rem'
          }}>
            <li style=${{ padding: '0.5rem 0' }}>✓ Browse and search book collection</li>
            <li style=${{ padding: '0.5rem 0' }}>✓ Download in multiple formats (EPUB, MOBI, FB2)</li>
            <li style=${{ padding: '0.5rem 0' }}>✓ Send books directly to Kindle</li>
            <li style=${{ padding: '0.5rem 0' }}>✓ Add personal notes and comments</li>
          </ul>
        </div>

        <a
          href="/login"
          style=${{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.75rem',
            padding: '1rem 2rem',
            background: '#4285f4',
            color: 'white',
            textDecoration: 'none',
            borderRadius: '8px',
            fontSize: '1.125rem',
            fontWeight: '600',
            boxShadow: '0 4px 12px rgba(66, 133, 244, 0.3)',
            transition: 'all 0.3s ease',
            border: 'none',
            cursor: 'pointer'
          }}
          onMouseOver=${(e) => {
            e.currentTarget.style.background = '#357ae8';
            e.currentTarget.style.transform = 'translateY(-2px)';
            e.currentTarget.style.boxShadow = '0 6px 16px rgba(66, 133, 244, 0.4)';
          }}
          onMouseOut=${(e) => {
            e.currentTarget.style.background = '#4285f4';
            e.currentTarget.style.transform = 'translateY(0)';
            e.currentTarget.style.boxShadow = '0 4px 12px rgba(66, 133, 244, 0.3)';
          }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="white">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
          </svg>
          Sign in with Google
        </a>

        <p style=${{
          marginTop: '2rem',
          fontSize: '0.75rem',
          color: '#95a5a6'
        }}>
          By signing in, you agree to our terms of service
        </p>
      </div>
    </div>
  `;
}

// Admin Panel Component
function AdminPanel() {
  const [jobs, setJobs] = useState([]);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [jobsRes, usersRes] = await Promise.all([
        api.get('/api/admin/jobs'),
        api.get('/api/admin/users/pending?limit=50&offset=0')
      ]);
      setJobs(jobsRes.data || []);
      setPendingUsers(usersRes.data.users || []);
    } catch (err) {
      console.error('Failed to load admin data:', err);
    } finally {
      setLoading(false);
    }
  };

  const startImport = async () => {
    if (!confirm('Start a new data import job?')) return;
    try {
      await api.post('/api/admin/import');
      alert('Import job started!');
      await loadData();
    } catch (err) {
      alert('Failed to start import: ' + err.message);
    }
  };

  const approveUser = async (userId) => {
    try {
      await api.post(`/api/admin/users/${userId}/approve`);
      await loadData();
    } catch (err) {
      alert('Failed to approve user: ' + err.message);
    }
  };

  const rejectUser = async (userId) => {
    try {
      await api.post(`/api/admin/users/${userId}/reject`);
      await loadData();
    } catch (err) {
      alert('Failed to reject user: ' + err.message);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2 style=${{ color: '#e74c3c' }}>Admin Panel</h2>

      <div style=${{ marginBottom: '2rem' }}>
        <div style=${{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3>Import Jobs</h3>
          <button onClick=${startImport} style=${{
            padding: '0.5rem 1rem',
            background: '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}>
            Start Import
          </button>
        </div>

        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          ${jobs.map(job => html`
            <div key=${job.id} style=${{
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <div>
                  <span style=${{ fontWeight: 'bold' }}>${job.jobType}</span>
                  <span style=${{
                    marginLeft: '0.5rem',
                    padding: '0.25rem 0.75rem',
                    borderRadius: '4px',
                    fontSize: '0.75rem',
                    background: job.status === 'COMPLETED' ? '#d5f4e6' :
                               job.status === 'FAILED' ? '#fadbd8' : '#fff3cd',
                    color: job.status === 'COMPLETED' ? '#27ae60' :
                           job.status === 'FAILED' ? '#e74c3c' : '#f39c12'
                  }}>
                    ${job.status}
                  </span>
                </div>
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                  Started: ${new Date(job.startedAt).toLocaleString()}
                </div>
              </div>

              ${job.progress && html`
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem', marginBottom: '0.5rem' }}>
                  ${job.progress}
                </div>
              `}

              <div style=${{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                gap: '0.5rem',
                fontSize: '0.875rem',
                color: '#34495e'
              }}>
                <div>Files: ${job.inpFilesProcessed}</div>
                <div>Added: ${job.booksAdded}</div>
                <div>Updated: ${job.booksUpdated}</div>
                <div>Deleted: ${job.booksDeleted}</div>
                <div>Covers: ${job.coversAdded}</div>
                ${job.bookErrors > 0 && html`<div style=${{ color: '#e74c3c' }}>Errors: ${job.bookErrors}</div>`}
              </div>

              ${job.errorMessage && html`
                <div style=${{ color: '#e74c3c', fontSize: '0.875rem', marginTop: '0.5rem' }}>
                  Error: ${job.errorMessage}
                </div>
              `}
            </div>
          `)}
        </div>
      </div>

      <div>
        <h3>Pending User Approvals (${pendingUsers.length})</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          ${pendingUsers.map(user => html`
            <div key=${user.id} style=${{
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div style=${{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                ${user.avatarUrl && html`
                  <img src=${user.avatarUrl} alt=${user.name}
                    style=${{ width: '48px', height: '48px', borderRadius: '50%' }}
                  />
                `}
                <div>
                  <div style=${{ fontWeight: 'bold' }}>${user.name}</div>
                  <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>${user.email}</div>
                  <div style=${{ color: '#95a5a6', fontSize: '0.75rem' }}>
                    Requested: ${new Date(user.createdAt).toLocaleString()}
                  </div>
                </div>
              </div>
              <div style=${{ display: 'flex', gap: '0.5rem' }}>
                <button onClick=${() => approveUser(user.id)} style=${{
                  padding: '0.5rem 1rem',
                  background: '#27ae60',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}>
                  Approve
                </button>
                <button onClick=${() => rejectUser(user.id)} style=${{
                  padding: '0.5rem 1rem',
                  background: '#e74c3c',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}>
                  Reject
                </button>
              </div>
            </div>
          `)}
        </div>

        ${pendingUsers.length === 0 && html`
          <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
            No pending user approvals
          </div>
        `}
      </div>
    </div>
  `;
}

// Error Page Component
function ErrorPage({ error, onRetry }) {
  return html`
    <div style=${{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: '#ecf0f1',
      padding: '2rem'
    }}>
      <div style=${{
        background: 'white',
        borderRadius: '16px',
        padding: '3rem',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        maxWidth: '500px',
        width: '100%',
        textAlign: 'center'
      }}>
        <div style=${{ fontSize: '4rem', marginBottom: '1rem' }}>⚠️</div>
        <h1 style=${{
          margin: '0 0 1rem 0',
          fontSize: '2rem',
          color: '#e74c3c'
        }}>
          Server Error
        </h1>
        <p style=${{
          margin: '0 0 2rem 0',
          color: '#7f8c8d',
          lineHeight: '1.6'
        }}>
          There was an error connecting to the server. This could be a database connection issue.
        </p>
        <div style=${{
          background: '#f8f9fa',
          padding: '1rem',
          borderRadius: '8px',
          marginBottom: '2rem',
          textAlign: 'left'
        }}>
          <p style=${{ margin: '0 0 0.5rem 0', fontWeight: 'bold', fontSize: '0.875rem' }}>
            Error details:
          </p>
          <p style=${{
            margin: 0,
            fontSize: '0.875rem',
            color: '#e74c3c',
            fontFamily: 'monospace',
            wordBreak: 'break-word'
          }}>
            ${error}
          </p>
        </div>
        <div style=${{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
          <button onClick=${onRetry} style=${{
            padding: '0.75rem 1.5rem',
            background: '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '1rem',
            fontWeight: '600',
            cursor: 'pointer'
          }}>
            Retry
          </button>
          <a href="/logout" style=${{
            padding: '0.75rem 1.5rem',
            background: '#95a5a6',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '1rem',
            fontWeight: '600',
            textDecoration: 'none',
            display: 'inline-block'
          }}>
            Logout
          </a>
        </div>
      </div>
    </div>
  `;
}

// Main App Component
function App() {
  const [user, setUser] = useState(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [currentView, setCurrentView] = useState('books');
  const [selectedBookId, setSelectedBookId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadUser();
  }, []);

  const loadUser = async () => {
    setLoading(true);
    setError(null);
    try {
      const userRes = await api.get('/api/me');
      console.log('User response:', userRes);
      setUser(userRes);

      try {
        const adminRes = await api.get('/api/admin/status');
        setIsAdmin(adminRes.data.isAdmin || false);
      } catch (err) {
        setIsAdmin(false);
      }
    } catch (err) {
      console.error('Failed to load user:', err);

      // Check if it's a 500 error (server error) vs 401 (not authenticated)
      if (err.message && err.message.includes('500')) {
        setError(err.message);
      } else {
        // Not authenticated, show login page
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleNavigate = (view) => {
    setCurrentView(view);
    setSelectedBookId(null);
  };

  const handleSelectBook = (bookId) => {
    setSelectedBookId(bookId);
  };

  const handleBackFromBook = () => {
    setSelectedBookId(null);
  };

  if (loading) {
    return html`
      <div style=${{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        background: '#ecf0f1'
      }}>
        <div style=${{ textAlign: 'center' }}>
          <h2>Loading...</h2>
        </div>
      </div>
    `;
  }

  // Show error page if there's a server error
  if (error) {
    return html`<${ErrorPage} error=${error} onRetry=${loadUser} />`;
  }

  // Show login page if user is not authenticated
  if (!user) {
    return html`<${LoginPage} />`;
  }

  return html`
    <div style=${{ minHeight: '100vh', background: '#ecf0f1' }}>
      <${Header}
        user=${user}
        onNavigate=${handleNavigate}
        currentView=${currentView}
        isAdmin=${isAdmin}
      />

      ${selectedBookId ? html`
        <${BookDetail}
          bookId=${selectedBookId}
          onBack=${handleBackFromBook}
          onRefresh=${() => {}}
        />
      ` : html`
        ${currentView === 'books' && html`
          <${BooksList} onSelectBook=${handleSelectBook} />
        `}
        ${currentView === 'starred' && html`
          <${BooksList} starred=${true} onSelectBook=${handleSelectBook} />
        `}
        ${currentView === 'activity' && html`
          <${Activity} />
        `}
        ${currentView === 'kindle' && html`
          <${KindleManagement} />
        `}
        ${currentView === 'admin' && isAdmin && html`
          <${AdminPanel} />
        `}
      `}
    </div>
  `;
}

// Render the app
render(html`<${App} />`, document.getElementById('app'));
