import { useState, useEffect } from 'preact/hooks';
import { html } from '../utils/html.js';
import { api } from '../utils/api.js';

export function BookDetail({ bookId, onBack, onRefresh }) {
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
        <- Back
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
