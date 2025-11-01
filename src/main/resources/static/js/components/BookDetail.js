import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';
import { BookCard } from './BookCard.js';

export function BookDetail({ bookId, onBack, onRefresh, onSelectBook }) {
  const [book, setBook] = useState(null);
  const [comments, setComments] = useState([]);
  const [similarBooks, setSimilarBooks] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [note, setNote] = useState('');
  const [noteEditMode, setNoteEditMode] = useState(false);
  const [noteEditValue, setNoteEditValue] = useState('');
  const [loading, setLoading] = useState(true);
  const [sendingToKindle, setSendingToKindle] = useState(false);

  useEffect(() => {
    loadBook();
    loadComments();
    loadSimilarBooks();
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

  const loadSimilarBooks = async () => {
    try {
      const res = await api.get(`/api/books/${bookId}/similar`);
      setSimilarBooks(res.data || []);
    } catch (err) {
      console.error('Failed to load similar books:', err);
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
      await loadSimilarBooks();
      onRefresh();
    } catch (err) {
      alert('Failed to toggle star: ' + err.message);
    }
  };

  const toggleBookStar = async (id, isStarred) => {
    try {
      if (isStarred) {
        await api.delete(`/api/books/${id}/star`);
      } else {
        await api.post(`/api/books/${id}/star`);
      }
      await loadSimilarBooks();
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

  const startEditingNote = () => {
    setNoteEditValue(note);
    setNoteEditMode(true);
  };

  const cancelEditingNote = () => {
    setNoteEditValue('');
    setNoteEditMode(false);
  };

  const saveNote = async (e) => {
    e.preventDefault();
    try {
      if (noteEditValue.trim()) {
        await api.post(`/api/books/${bookId}/notes`, { note: noteEditValue });
        setNote(noteEditValue);
      } else {
        await api.delete(`/api/books/${bookId}/notes`);
        setNote('');
      }
      setNoteEditMode(false);
      setNoteEditValue('');
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
    return h('div', { style: { padding: '2rem', textAlign: 'center' } }, 'Loading...');
  }

  if (!book) {
    return h('div', { style: { padding: '2rem' } }, 'Book not found');
  }

  return h('div', { style: { padding: '2rem' } },
    h('button', {
      onClick: onBack,
      style: {
        marginBottom: '1rem',
        padding: '0.5rem 1rem',
        background: '#95a5a6',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer'
      }
    }, '← Back'),

    h('div', { style: { display: 'grid', gridTemplateColumns: '300px 1fr', gap: '2rem', marginBottom: '2rem' } },
      h('div', null,
        book.coverImageUrl && h('img', {
          src: book.coverImageUrl,
          alt: book.title,
          style: { width: '100%', borderRadius: '8px', marginBottom: '1rem' }
        }),

        h('button', {
          onClick: toggleStar,
          style: {
            width: '100%',
            padding: '0.75rem',
            background: book.isStarred ? '#f39c12' : '#ecf0f1',
            color: book.isStarred ? 'white' : '#34495e',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            marginBottom: '0.5rem',
            fontWeight: 'bold'
          }
        }, book.isStarred ? '⭐ Starred' : '☆ Star this book'),

        h('div', { style: { marginTop: '1rem' } },
          h('h4', { style: { marginBottom: '0.5rem' } }, 'Download'),
          h('button', {
            onClick: () => download('fb2'),
            style: {
              width: '100%',
              padding: '0.5rem',
              background: '#3498db',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              marginBottom: '0.25rem'
            }
          }, 'Download FB2'),
          h('button', {
            onClick: () => download('epub'),
            style: {
              width: '100%',
              padding: '0.5rem',
              background: '#3498db',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              marginBottom: '0.25rem'
            }
          }, 'Download EPUB'),
          h('button', {
            onClick: () => download('mobi'),
            style: {
              width: '100%',
              padding: '0.5rem',
              background: '#3498db',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }
          }, 'Download MOBI')
        )
      ),

      h('div', null,
        h('h1', { style: { marginTop: 0 } }, book.title),
        h('p', { style: { fontSize: '1.125rem', color: '#7f8c8d', marginBottom: '1rem' } },
          book.authors.map(a => a.fullName).join(', ')
        ),

        book.series && h('p', { style: { color: '#95a5a6', marginBottom: '1rem' } },
          'Series: ', book.series.name, book.seriesNumber ? ` #${book.seriesNumber}` : ''
        ),

        h('div', { style: { display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' } },
          book.genres && book.genres.map(genre =>
            h('span', {
              key: genre,
              style: {
                background: '#ecf0f1',
                padding: '0.5rem 1rem',
                borderRadius: '4px',
                color: '#34495e'
              }
            }, genre)
          ),
          h('span', {
            style: {
              background: '#e8f4f8',
              padding: '0.5rem 1rem',
              borderRadius: '4px',
              color: '#2980b9'
            }
          }, book.language)
        ),

        book.annotation && h('div', {
          style: {
            background: '#f8f9fa',
            padding: '1rem',
            borderRadius: '8px',
            marginBottom: '2rem',
            whiteSpace: 'pre-wrap'
          }
        }, book.annotation),

        h('div', { style: { marginBottom: '2rem' } },
          h('h3', null, 'My Note'),
          h('p', { style: { color: '#7f8c8d', fontSize: '0.875rem', marginBottom: '0.5rem' } },
            'Personal notes are private and only visible to you.'
          ),
          !noteEditMode ? (
            h('div', null,
              note ? (
                h('div', {
                  style: {
                    background: '#f8f9fa',
                    padding: '1rem',
                    borderRadius: '8px',
                    marginBottom: '0.5rem',
                    whiteSpace: 'pre-wrap',
                    minHeight: '50px'
                  }
                }, note)
              ) : (
                h('div', {
                  style: {
                    background: '#f8f9fa',
                    padding: '1rem',
                    borderRadius: '8px',
                    marginBottom: '0.5rem',
                    color: '#95a5a6',
                    fontStyle: 'italic',
                    minHeight: '50px'
                  }
                }, 'No note added yet.')
              ),
              h('button', {
                onClick: startEditingNote,
                style: {
                  padding: '0.5rem 1rem',
                  background: '#3498db',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }
              }, 'Edit')
            )
          ) : (
            h('form', { onSubmit: saveNote },
              h('textarea', {
                value: noteEditValue,
                onInput: (e) => setNoteEditValue(e.target.value),
                placeholder: 'Add your personal note...',
                style: {
                  width: '100%',
                  minHeight: '100px',
                  padding: '0.75rem',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '1rem',
                  marginBottom: '0.5rem',
                  boxSizing: 'border-box'
                }
              }),
              h('div', { style: { display: 'flex', gap: '0.5rem' } },
                h('button', {
                  type: 'submit',
                  style: {
                    padding: '0.5rem 1rem',
                    background: '#27ae60',
                    color: 'white',
                    border: 'none',
                    borderRadius: '4px',
                    cursor: 'pointer'
                  }
                }, 'Save'),
                h('button', {
                  type: 'button',
                  onClick: cancelEditingNote,
                  style: {
                    padding: '0.5rem 1rem',
                    background: '#95a5a6',
                    color: 'white',
                    border: 'none',
                    borderRadius: '4px',
                    cursor: 'pointer'
                  }
                }, 'Cancel')
              )
            )
          )
        ),

        h('div', null,
          h('h3', null, 'Comments'),
          h('form', { onSubmit: addComment, style: { marginBottom: '1rem' } },
            h('textarea', {
              value: newComment,
              onInput: (e) => setNewComment(e.target.value),
              placeholder: 'Add a comment...',
              style: {
                width: '100%',
                minHeight: '80px',
                padding: '0.75rem',
                border: '1px solid #ddd',
                borderRadius: '4px',
                fontSize: '1rem',
                marginBottom: '0.5rem',
                boxSizing: 'border-box'
              }
            }),
            h('button', {
              type: 'submit',
              style: {
                padding: '0.5rem 1rem',
                background: '#3498db',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }
            }, 'Add Comment')
          ),

          h('div', { style: { display: 'flex', flexDirection: 'column', gap: '1rem' } },
            comments.map(comment =>
              h('div', {
                key: comment.id,
                style: {
                  border: '1px solid #e1e8ed',
                  borderRadius: '8px',
                  padding: '1rem',
                  background: 'white'
                }
              },
                h('div', { style: { display: 'flex', gap: '0.75rem', marginBottom: '0.5rem' } },
                  comment.userAvatarUrl && h('img', {
                    src: comment.userAvatarUrl,
                    alt: comment.userName,
                    style: { width: '40px', height: '40px', borderRadius: '50%' }
                  }),
                  h('div', { style: { flex: 1 } },
                    h('div', { style: { fontWeight: 'bold', marginBottom: '0.25rem' } },
                      comment.userName
                    ),
                    h('div', { style: { color: '#95a5a6', fontSize: '0.875rem' } },
                      new Date(comment.createdAt).toLocaleString()
                    )
                  ),
                  h('button', {
                    onClick: () => deleteComment(comment.id),
                    style: {
                      background: 'none',
                      border: 'none',
                      color: '#e74c3c',
                      cursor: 'pointer',
                      fontSize: '0.875rem'
                    }
                  }, 'Delete')
                ),
                h('p', { style: { margin: '0', whiteSpace: 'pre-wrap' } }, comment.comment)
              )
            )
          )
        )
      )
    ),

    similarBooks.length > 0 && h('div', { style: { marginTop: '3rem' } },
      h('h2', { style: { marginBottom: '1rem' } }, 'Similar Books'),
      h('div', {
        style: {
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))',
          gap: '1rem'
        }
      },
        similarBooks.map(book =>
          h(BookCard, {
            key: book.id,
            book: book,
            onSelect: onSelectBook,
            onToggleStar: toggleBookStar
          })
        )
      )
    )
  );
}
