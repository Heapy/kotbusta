import { h, render } from 'preact';
import { useState, useEffect } from 'preact/hooks';

// API utility functions
const api = {
    async get(url) {
        const response = await fetch(url, { credentials: 'include' });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    },
    
    async post(url, data) {
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(data)
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    },
    
    async delete(url) {
        const response = await fetch(url, {
            method: 'DELETE',
            credentials: 'include'
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }
};

// Router utility
function useRouter() {
    const [route, setRoute] = useState(window.location.pathname);
    
    useEffect(() => {
        const handlePopState = () => {
            setRoute(window.location.pathname);
        };
        window.addEventListener('popstate', handlePopState);
        return () => window.removeEventListener('popstate', handlePopState);
    }, []);
    
    const navigate = (path) => {
        if (path !== route) {
            window.history.pushState({}, '', path);
            setRoute(path);
        }
    };
    
    return [route, navigate];
}

// Loading component
function Loading() {
    return h('div', { className: 'loading' }, 
        h('div', { className: 'spinner' })
    );
}

// Error component
function ErrorMessage({ message }) {
    return h('div', { className: 'error' }, message);
}

// Header component
function Header({ user, isAdmin, onNavigate }) {
    return h('header', { className: 'header' },
        h('div', { className: 'container' },
            h('div', { className: 'header-content' },
                h('a', { 
                    href: '/', 
                    className: 'logo',
                    onClick: (e) => { e.preventDefault(); onNavigate('/'); }
                }, 'Kotbusta'),
                h('nav', { className: 'nav' },
                    h('a', { 
                        href: '/', 
                        className: 'nav-link',
                        onClick: (e) => { e.preventDefault(); onNavigate('/'); }
                    }, 'Books'),
                    user && h('a', { 
                        href: '/starred', 
                        className: 'nav-link',
                        onClick: (e) => { e.preventDefault(); onNavigate('/starred'); }
                    }, 'Starred'),
                    h('a', { 
                        href: '/activity', 
                        className: 'nav-link',
                        onClick: (e) => { e.preventDefault(); onNavigate('/activity'); }
                    }, 'Activity'),
                    isAdmin && h('a', { 
                        href: '/admin', 
                        className: 'nav-link admin-link',
                        onClick: (e) => { e.preventDefault(); onNavigate('/admin'); }
                    }, 'Admin')
                ),
                h('div', { className: 'user-menu' },
                    user ? [
                        user.avatarUrl && h('img', { 
                            src: user.avatarUrl, 
                            alt: user.name,
                            className: 'user-avatar'
                        }),
                        h('span', null, user.name),
                        h('a', { href: '/logout', className: 'btn btn-secondary btn-sm' }, 'Logout')
                    ] : h('a', { href: '/login', className: 'btn btn-primary' }, 'Login with Google')
                )
            )
        )
    );
}

// Search component
function SearchBar({ onSearch, filters, onFilterChange }) {
    const [query, setQuery] = useState('');
    
    const handleSubmit = (e) => {
        e.preventDefault();
        onSearch(query);
    };
    
    return h('section', { className: 'search-section' },
        h('div', { className: 'container' },
            h('form', { className: 'search-bar', onSubmit: handleSubmit },
                h('input', {
                    type: 'text',
                    placeholder: 'Search books, authors...',
                    className: 'search-input',
                    value: query,
                    onInput: (e) => setQuery(e.target.value)
                }),
                h('button', { type: 'submit', className: 'btn btn-primary' }, 'Search')
            ),
            h('div', { className: 'search-filters' },
                h('select', {
                    className: 'filter-select',
                    value: filters.genre || '',
                    onChange: (e) => onFilterChange({ ...filters, genre: e.target.value || null })
                }, [
                    h('option', { value: '' }, 'All Genres'),
                    h('option', { value: 'sf' }, 'Science Fiction'),
                    h('option', { value: 'det_classic' }, 'Detective'),
                    h('option', { value: 'prose_contemporary' }, 'Contemporary'),
                    h('option', { value: 'thriller' }, 'Thriller')
                ]),
                h('select', {
                    className: 'filter-select',
                    value: filters.language || '',
                    onChange: (e) => onFilterChange({ ...filters, language: e.target.value || null })
                }, [
                    h('option', { value: '' }, 'All Languages'),
                    h('option', { value: 'ru' }, 'Russian'),
                    h('option', { value: 'en' }, 'English'),
                    h('option', { value: 'uk' }, 'Ukrainian')
                ])
            )
        )
    );
}

// Book card component
function BookCard({ book, onStar, user, onNavigate }) {
    const handleStar = async (e) => {
        e.stopPropagation();
        if (!user) return;
        await onStar(book.id, !book.isStarred);
    };
    
    return h('div', { 
        className: 'book-card',
        onClick: () => onNavigate(`/books/${book.id}`)
    }, [
        h('div', { className: 'book-cover' },
            book.coverImageUrl 
                ? h('img', { src: book.coverImageUrl, alt: book.title })
                : h('div', null, 'No Cover')
        ),
        h('div', { className: 'book-info' }, [
            h('h3', { className: 'book-title' }, book.title),
            h('p', { className: 'book-authors' }, book.authors.join(', ')),
            h('div', { className: 'book-meta' }, [
                book.genre && h('span', { className: 'book-genre' }, book.genre),
                user && h('button', {
                    className: `star-button ${book.isStarred ? 'starred' : ''}`,
                    onClick: handleStar,
                    title: book.isStarred ? 'Remove from favorites' : 'Add to favorites'
                }, book.isStarred ? '★' : '☆')
            ])
        ])
    ]);
}

// Books grid component
function BooksGrid({ books, loading, error, onStar, user, onNavigate }) {
    if (loading) return Loading();
    if (error) return ErrorMessage({ message: error });
    if (!books.length) return h('p', { className: 'text-center' }, 'No books found');
    
    return h('div', { className: 'books-grid' },
        books.map(book => 
            BookCard({ book, onStar, user, onNavigate, key: book.id })
        )
    );
}

// Book detail component
function BookDetail({ bookId, user, onNavigate }) {
    const [book, setBook] = useState(null);
    const [comments, setComments] = useState([]);
    const [similarBooks, setSimilarBooks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [newComment, setNewComment] = useState('');
    const [userNote, setUserNote] = useState('');
    
    useEffect(() => {
        loadBookDetail();
    }, [bookId]);
    
    const loadBookDetail = async () => {
        try {
            setLoading(true);
            const [bookResponse, commentsResponse, similarResponse] = await Promise.all([
                api.get(`/api/books/${bookId}`),
                api.get(`/api/books/${bookId}/comments`),
                api.get(`/api/books/${bookId}/similar`)
            ]);
            
            setBook(bookResponse.data);
            setComments(commentsResponse.data);
            setSimilarBooks(similarResponse.data);
            setUserNote(bookResponse.data.userNote || '');
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };
    
    const handleStar = async () => {
        if (!user || !book) return;
        try {
            if (book.isStarred) {
                await api.delete(`/api/books/${bookId}/star`);
            } else {
                await api.post(`/api/books/${bookId}/star`);
            }
            setBook({ ...book, isStarred: !book.isStarred });
        } catch (err) {
            console.error('Failed to toggle star:', err);
        }
    };
    
    const handleAddComment = async (e) => {
        e.preventDefault();
        if (!user || !newComment.trim()) return;
        
        try {
            const response = await api.post(`/api/books/${bookId}/comments`, {
                comment: newComment
            });
            setComments([response.data, ...comments]);
            setNewComment('');
        } catch (err) {
            console.error('Failed to add comment:', err);
        }
    };
    
    const handleSaveNote = async (e) => {
        e.preventDefault();
        if (!user) return;
        
        try {
            await api.post(`/api/books/${bookId}/notes`, {
                note: userNote,
                isPrivate: true
            });
        } catch (err) {
            console.error('Failed to save note:', err);
        }
    };
    
    const handleDownload = (format) => {
        if (!user) return;
        window.open(`/api/books/${bookId}/download/${format}`, '_blank');
    };
    
    if (loading) return Loading();
    if (error) return ErrorMessage({ message: error });
    if (!book) return ErrorMessage({ message: 'Book not found' });
    
    return h('div', null, [
        h('div', { className: 'book-detail' }, [
            h('img', {
                src: book.coverImageUrl,
                alt: book.title,
                className: 'book-detail-cover'
            }),
            h('div', { className: 'book-detail-info' }, [
                h('h1', null, book.title),
                h('p', { className: 'book-detail-authors' }, 
                    book.authors.map(a => a.fullName).join(', ')
                ),
                h('div', { className: 'book-detail-meta' }, [
                    book.genre && h('span', { className: 'meta-item' }, ['📚 ', book.genre]),
                    h('span', { className: 'meta-item' }, ['🌐 ', book.language]),
                    book.series && h('span', { className: 'meta-item' }, 
                        ['📖 ', book.series.name, book.seriesNumber ? ` #${book.seriesNumber}` : '']
                    )
                ]),
                h('div', { className: 'book-actions' }, [
                    user && h('button', {
                        className: `btn ${book.isStarred ? 'btn-secondary' : 'btn-primary'}`,
                        onClick: handleStar
                    }, book.isStarred ? '★ Starred' : '☆ Star'),
                    h('button', {
                        className: 'btn btn-secondary',
                        onClick: () => handleDownload('fb2')
                    }, 'Download FB2'),
                    h('button', {
                        className: 'btn btn-secondary',
                        onClick: () => handleDownload('epub')
                    }, 'Download EPUB'),
                    h('button', {
                        className: 'btn btn-secondary',
                        onClick: () => handleDownload('mobi')
                    }, 'Download MOBI')
                ]),
                book.annotation && h('div', { className: 'book-annotation' }, book.annotation)
            ])
        ]),
        
        // User notes section
        user && h('section', { className: 'notes-section' }, [
            h('h2', { className: 'section-title' }, 'My Notes'),
            h('form', { className: 'note-form', onSubmit: handleSaveNote }, [
                h('textarea', {
                    className: 'textarea',
                    placeholder: 'Add your private notes about this book...',
                    value: userNote,
                    onInput: (e) => setUserNote(e.target.value)
                }),
                h('button', { type: 'submit', className: 'btn btn-primary' }, 'Save Note')
            ])
        ]),
        
        // Comments section
        h('section', { className: 'comments-section' }, [
            h('h2', { className: 'section-title' }, 'Comments'),
            user && h('form', { className: 'comment-form', onSubmit: handleAddComment }, [
                h('textarea', {
                    className: 'textarea',
                    placeholder: 'Share your thoughts about this book...',
                    value: newComment,
                    onInput: (e) => setNewComment(e.target.value)
                }),
                h('button', { type: 'submit', className: 'btn btn-primary' }, 'Add Comment')
            ]),
            h('div', { className: 'comments-list' },
                comments.map(comment =>
                    h('div', { className: 'comment', key: comment.id }, [
                        h('div', { className: 'comment-header' }, [
                            comment.userAvatarUrl && h('img', {
                                src: comment.userAvatarUrl,
                                alt: comment.userName,
                                className: 'comment-avatar'
                            }),
                            h('span', { className: 'comment-author' }, comment.userName),
                            h('span', { className: 'comment-date' }, 
                                new Date(comment.createdAt * 1000).toLocaleDateString()
                            )
                        ]),
                        h('p', { className: 'comment-text' }, comment.comment)
                    ])
                )
            )
        ]),
        
        // Similar books section
        similarBooks.length > 0 && h('section', null, [
            h('h2', { className: 'section-title' }, 'Similar Books'),
            BooksGrid({ 
                books: similarBooks, 
                loading: false, 
                error: null, 
                onStar: () => {}, 
                user, 
                onNavigate 
            })
        ])
    ]);
}

// Starred books page component
function StarredPage({ user, onNavigate }) {
    const [starredBooks, setStarredBooks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    useEffect(() => {
        loadStarredBooks();
    }, []);
    
    const loadStarredBooks = async () => {
        try {
            const response = await api.get('/api/books/starred');
            setStarredBooks(response.data.books);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };
    
    const handleStar = async (bookId, starred) => {
        if (!user) return;
        
        try {
            if (starred) {
                await api.post(`/api/books/${bookId}/star`);
            } else {
                await api.delete(`/api/books/${bookId}/star`);
                // Remove from starred list when unstarred
                setStarredBooks(starredBooks.filter(book => book.id !== bookId));
            }
            
            // Update book in the list
            setStarredBooks(starredBooks.map(book => 
                book.id === bookId ? { ...book, isStarred: starred } : book
            ));
        } catch (err) {
            console.error('Failed to toggle star:', err);
        }
    };
    
    return h('div', null, [
        h('h1', { className: 'page-title' }, 'Starred Books'),
        BooksGrid({ 
            books: starredBooks, 
            loading, 
            error, 
            onStar: handleStar, 
            user, 
            onNavigate 
        })
    ]);
}

// Activity page component
function ActivityPage() {
    const [activity, setActivity] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    useEffect(() => {
        loadActivity();
    }, []);
    
    const loadActivity = async () => {
        try {
            const response = await api.get('/api/activity');
            setActivity(response.data);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };
    
    if (loading) return Loading();
    if (error) return ErrorMessage({ message: error });
    if (!activity) return null;
    
    return h('div', null, [
        h('h1', { className: 'page-title' }, 'Recent Activity'),
        h('div', { className: 'activity-feed' }, [
            ...activity.comments.map(comment =>
                h('div', { className: 'activity-item', key: `comment-${comment.id}` }, [
                    h('div', { className: 'activity-header' }, [
                        h('span', { className: 'activity-type activity-comment' }, 'Comment'),
                        h('span', null, comment.userName),
                        h('span', null, 'commented on'),
                        h('strong', null, comment.bookTitle)
                    ]),
                    h('p', null, comment.comment)
                ])
            ),
            ...activity.downloads.map(download =>
                h('div', { className: 'activity-item', key: `download-${download.id}` }, [
                    h('div', { className: 'activity-header' }, [
                        h('span', { className: 'activity-type activity-download' }, 'Download'),
                        h('span', null, download.userName),
                        h('span', null, `downloaded ${download.format.toUpperCase()}`),
                        h('strong', null, download.bookTitle)
                    ])
                ])
            )
        ])
    ]);
}

// Admin page component
function AdminPage({ user, isAdmin }) {
    const [jobs, setJobs] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [importForm, setImportForm] = useState({
        extractCovers: false,
        maxCoverArchives: 3
    });
    const [coverForm, setCoverForm] = useState({
        maxArchives: 10
    });
    
    useEffect(() => {
        if (isAdmin && user) {
            loadJobs();
            const interval = setInterval(loadJobs, 2000); // Poll every 2 seconds
            return () => clearInterval(interval);
        }
    }, [isAdmin, user]);
    
    const loadJobs = async () => {
        try {
            const response = await api.get('/api/admin/jobs');
            setJobs(response.data);
        } catch (err) {
            setError(err.message);
        }
    };
    
    const handleStartImport = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        
        try {
            await api.post('/api/admin/import', importForm);
            await loadJobs();
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };
    
    const handleStartCoverExtraction = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        
        try {
            await api.post('/api/admin/extract-covers', coverForm);
            await loadJobs();
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };
    
    if (!user) {
        return h('div', { className: 'login-prompt' }, [
            h('h1', null, 'Authentication Required'),
            h('p', null, 'Please log in to access the admin panel'),
            h('a', { href: '/login', className: 'btn btn-primary' }, 'Login with Google')
        ]);
    }
    
    if (!isAdmin) {
        return h('div', { className: 'access-denied' }, [
            h('h1', null, 'Access Denied'),
            h('p', null, 'You do not have admin privileges')
        ]);
    }
    
    return h('div', null, [
        h('h1', { className: 'page-title' }, 'Admin Panel'),
        
        error && ErrorMessage({ message: error }),
        
        // Data Import Section
        h('section', { className: 'admin-section' }, [
            h('h2', { className: 'section-title' }, 'Data Import'),
            h('form', { className: 'admin-form', onSubmit: handleStartImport }, [
                h('div', { className: 'form-group' }, [
                    h('label', null, [
                        h('input', {
                            type: 'checkbox',
                            checked: importForm.extractCovers,
                            onChange: (e) => setImportForm({
                                ...importForm,
                                extractCovers: e.target.checked
                            })
                        }),
                        ' Extract book covers during import'
                    ])
                ]),
                importForm.extractCovers && h('div', { className: 'form-group' }, [
                    h('label', null, 'Max cover archives to process:'),
                    h('input', {
                        type: 'number',
                        min: 1,
                        max: 50,
                        value: importForm.maxCoverArchives,
                        onChange: (e) => setImportForm({
                            ...importForm,
                            maxCoverArchives: parseInt(e.target.value) || 3
                        })
                    })
                ]),
                h('button', {
                    type: 'submit',
                    className: 'btn btn-primary',
                    disabled: loading
                }, loading ? 'Starting...' : 'Start Data Import')
            ])
        ]),
        
        // Cover Extraction Section
        h('section', { className: 'admin-section' }, [
            h('h2', { className: 'section-title' }, 'Cover Extraction'),
            h('form', { className: 'admin-form', onSubmit: handleStartCoverExtraction }, [
                h('div', { className: 'form-group' }, [
                    h('label', null, 'Max archives to process:'),
                    h('input', {
                        type: 'number',
                        min: 1,
                        max: 100,
                        value: coverForm.maxArchives,
                        onChange: (e) => setCoverForm({
                            ...coverForm,
                            maxArchives: parseInt(e.target.value) || 10
                        })
                    })
                ]),
                h('button', {
                    type: 'submit',
                    className: 'btn btn-primary',
                    disabled: loading
                }, loading ? 'Starting...' : 'Start Cover Extraction')
            ])
        ]),
        
        // Jobs Status Section
        h('section', { className: 'admin-section' }, [
            h('h2', { className: 'section-title' }, 'Job Status'),
            jobs.length === 0 ? 
                h('p', null, 'No jobs found') :
                h('div', { className: 'jobs-list' },
                    jobs.map(job =>
                        h('div', { 
                            className: `job-item job-${job.status}`,
                            key: job.id 
                        }, [
                            h('div', { className: 'job-header' }, [
                                h('span', { className: 'job-type' }, 
                                    job.type === 'data_import' ? 'Data Import' : 'Cover Extraction'
                                ),
                                h('span', { className: `job-status job-status-${job.status}` }, 
                                    job.status.charAt(0).toUpperCase() + job.status.slice(1)
                                ),
                                h('span', { className: 'job-time' },
                                    new Date(job.startTime).toLocaleString()
                                )
                            ]),
                            h('div', { className: 'job-progress' }, job.progress),
                            job.errorMessage && h('div', { className: 'job-error' }, job.errorMessage),
                            job.endTime && h('div', { className: 'job-duration' }, 
                                `Duration: ${Math.round((job.endTime - job.startTime) / 1000)}s`
                            )
                        ])
                    )
                )
        ])
    ]);
}

// Main app component
function App() {
    const [route, navigate] = useRouter();
    const [user, setUser] = useState(null);
    const [isAdmin, setIsAdmin] = useState(false);
    const [books, setBooks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filters, setFilters] = useState({});
    
    useEffect(() => {
        checkAuth();
    }, []);
    
    useEffect(() => {
        // Handle route changes
        if (route === '/admin' && user && !isAdmin) {
            checkAuth();
        }
        // Clear any previous errors when route changes
        setError(null);
    }, [route]);
    
    useEffect(() => {
        if (user && route === '/') {
            loadBooks();
        }
    }, [searchQuery, filters, user, route]);
    
    const checkAuth = async () => {
        try {
            const response = await api.get('/api/user/info');
            if (response.success) {
                setUser(response.data);
                
                // Check admin status
                try {
                    const adminResponse = await api.get('/api/admin/status');
                    setIsAdmin(adminResponse.success);
                } catch (adminErr) {
                    setIsAdmin(false);
                }
            }
        } catch (err) {
            // User is not authenticated
            setUser(null);
            setIsAdmin(false);
        } finally {
            setLoading(false);
        }
    };
    
    const loadBooks = async () => {
        if (!user) {
            setBooks([]);
            setLoading(false);
            return;
        }
        
        try {
            setLoading(true);
            setError(null);
            
            let url = '/api/books';
            const params = new URLSearchParams();
            
            if (searchQuery) {
                url = '/api/books/search';
                params.set('q', searchQuery);
            }
            
            if (filters.genre) params.set('genre', filters.genre);
            if (filters.language) params.set('language', filters.language);
            
            if (params.toString()) {
                url += '?' + params.toString();
            }
            
            const response = await api.get(url);
            setBooks(response.data.books);
        } catch (err) {
            setError(err.message);
            setBooks([]);
        } finally {
            setLoading(false);
        }
    };
    
    const handleSearch = (query) => {
        setSearchQuery(query);
        if (route !== '/') navigate('/');
    };
    
    const handleStar = async (bookId, starred) => {
        if (!user) return;
        
        try {
            if (starred) {
                await api.post(`/api/books/${bookId}/star`);
            } else {
                await api.delete(`/api/books/${bookId}/star`);
            }
            
            // Update book in the list
            setBooks(books.map(book => 
                book.id === bookId ? { ...book, isStarred: starred } : book
            ));
        } catch (err) {
            console.error('Failed to toggle star:', err);
        }
    };
    
    // Route matching
    const bookMatch = route.match(/^\/books\/(\d+)$/);
    const bookId = bookMatch ? parseInt(bookMatch[1]) : null;
    
    return h('div', null, [
        Header({ user, isAdmin, onNavigate: navigate }),
        
        route === '/' && [
            user && SearchBar({ onSearch: handleSearch, filters, onFilterChange: setFilters }),
            h('main', { className: 'main' },
                h('div', { className: 'container' }, [
                    user ? [
                        h('h1', { className: 'page-title' }, 'Books'),
                        BooksGrid({ books, loading, error, onStar: handleStar, user, onNavigate: navigate })
                    ] : [
                        h('div', { className: 'login-prompt' }, [
                            h('h1', null, 'Welcome to Kotbusta'),
                            h('p', null, 'Please log in with Google to access your digital library'),
                            h('a', { href: '/login', className: 'btn btn-primary btn-large' }, 'Login with Google')
                        ])
                    ]
                ])
            )
        ],
        
        bookId && h('main', { className: 'main' },
            h('div', { className: 'container' },
                user ? BookDetail({ bookId, user, onNavigate: navigate }) : [
                    h('div', { className: 'login-prompt' }, [
                        h('h1', null, 'Authentication Required'),
                        h('p', null, 'Please log in to view book details'),
                        h('a', { href: '/login', className: 'btn btn-primary' }, 'Login with Google')
                    ])
                ]
            )
        ),
        
        route === '/starred' && h('main', { className: 'main' },
            h('div', { className: 'container' },
                user ? StarredPage({ user, onNavigate: navigate }) : [
                    h('div', { className: 'login-prompt' }, [
                        h('h1', null, 'Authentication Required'),
                        h('p', null, 'Please log in to view starred books'),
                        h('a', { href: '/login', className: 'btn btn-primary' }, 'Login with Google')
                    ])
                ]
            )
        ),
        
        route === '/activity' && h('main', { className: 'main' },
            h('div', { className: 'container' },
                user ? ActivityPage() : [
                    h('div', { className: 'login-prompt' }, [
                        h('h1', null, 'Authentication Required'),
                        h('p', null, 'Please log in to view activity'),
                        h('a', { href: '/login', className: 'btn btn-primary' }, 'Login with Google')
                    ])
                ]
            )
        ),
        
        route === '/admin' && h('main', { className: 'main' },
            h('div', { className: 'container' },
                AdminPage({ user, isAdmin })
            )
        )
    ]);
}

// Render the app
render(h(App), document.getElementById('app'));