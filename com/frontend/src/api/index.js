import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// JWT 토큰 자동 첨부
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 401 → 자동 로그아웃
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default {
  // ── 도서 검색 ──────────────────────────────────────────
  searchBooks(q) {
    return api.get('/books/search', { params: { q } })
  },

  getBookDetail(isbn13) {
    return api.get(`/books/${isbn13}`)
  },

  getEbookInfo(isbn13) {
    return api.get(`/books/${isbn13}/ebook`)
  },

  checkLibrary(isbn) {
    return api.get('/books/library-check', { params: { isbn } })
  },

  getBookPrices(isbn, title) {
    return api.get('/books/prices', { params: { isbn, title } })
  },

  getBooksInfinite(cursor, limit = 30) {
    return api.get('/books/infinite', { params: { cursor, limit } })
  },

  // ── 공지사항 배너 ─────────────────────────────────────
  getNoticeBanners() {
    return api.get('/notices/banners')
  },

  // ── 인증 ──────────────────────────────────────────────
  login(username, password) {
    return api.post('/auth/login', { username, password })
  },

  register(data) {
    return api.post('/auth/register', data)
  },

  validateUser(username, studentId, password) {
    return api.get('/auth/validate', { params: { username, studentId, password } })
  },

  // ── 프로필 ────────────────────────────────────────────
  getMyProfile() {
    return api.get('/users/me')
  },

  // ── 독서 일지 ──────────────────────────────────────────
  getReadingLogs() {
    return api.get('/reading-logs')
  },

  createReadingLog(data) {
    // data: { bookTitle, isbn?, pagesRead?, memo?, logDate: 'YYYY-MM-DD' }
    return api.post('/reading-logs', data)
  },

  deleteReadingLog(id) {
    return api.delete(`/reading-logs/${id}`)
  }
}
