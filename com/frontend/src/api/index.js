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

  getBookDetailInfo(isbn13) {
    return api.get(`/books/${isbn13}/detail-info`)
  },

  getBooksInfinite(cursor, limit = 30) {
    return api.get('/books/infinite', { params: { cursor, limit } })
  },

  // ── 리뷰 (댓글 통합) ──────────────────────────────────
  getReviews(bookId) {
    return api.get(`/books/${bookId}/reviews`)
  },

  createReview(bookId, content, rating = 3) {
    return api.post(`/books/${bookId}/reviews`, { content, rating })
  },

  deleteReview(bookId, reviewId) {
    return api.delete(`/books/${bookId}/reviews/${reviewId}`)
  },

  toggleReviewLike(bookId, reviewId) {
    return api.post(`/books/${bookId}/reviews/${reviewId}/like`)
  },

  getDifficultyHeatmap(bookId) {
    return api.get(`/books/${bookId}/difficulty-heatmap`)
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

  findUsername(studentId, fullName) {
    return api.post('/auth/find-username', { studentId, fullName })
  },

  resetPassword(username, studentId, newPassword) {
    return api.post('/auth/reset-password', { username, studentId, newPassword })
  },

  // ── 프로필 ────────────────────────────────────────────
  getMyProfile() {
    return api.get('/users/me')
  },

  getMyRecentBooks() {
    return api.get('/users/me/recent-books')
  },

  // ── 장바구니 ───────────────────────────────────────────
  getCart() {
    return api.get('/cart')
  },

  addToCart(bookId, quantity = 1) {
    return api.post('/cart', { bookId, quantity })
  },

  removeFromCart(bookId) {
    return api.delete(`/cart/${encodeURIComponent(bookId)}`)
  },

  // ── 독서 일지 ──────────────────────────────────────────
  getReadingLogs() {
    return api.get('/reading-logs')
  },

  createReadingLog(data) {
    return api.post('/reading-logs', data)
  },

  deleteReadingLog(id) {
    return api.delete(`/reading-logs/${id}`)
  },

  // ── 인기 검색어 ────────────────────────────────────────
  getPopularKeywords() {
    return api.get('/search/popular')
  },

  // ── 학과 ──────────────────────────────────────────────
  getDepartments() {
    return api.get('/departments')
  },

  // ── 중고 서적 ──────────────────────────────────────────
  getUsedBooks(departmentId, page = 0, size = 20) {
    return api.get('/used-books', { params: { department_id: departmentId, page, size } })
  },

  getUsedBookDetail(id) {
    return api.get(`/used-books/${id}`)
  },

  createUsedBook(formData) {
    return api.post('/used-books', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  searchUsedBooks(q, page = 0, size = 20) {
    return api.get('/used-books/search', { params: { q, page, size } })
  }
}
