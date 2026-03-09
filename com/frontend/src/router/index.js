import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue')
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue')
  },
  {
    path: '/books/:isbn13',
    name: 'book-detail',
    component: () => import('@/views/BookDetailView.vue')
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue')
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/account-recovery',
    name: 'account-recovery',
    component: () => import('@/views/AccountRecoveryView.vue')
  },
  {
    path: '/market',
    name: 'market',
    component: () => import('@/views/UsedMarketView.vue')
  },
  {
    path: '/market/write',
    name: 'market-write',
    component: () => import('@/views/UsedMarketWriteView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/market/:id',
    name: 'market-detail',
    component: () => import('@/views/UsedMarketDetailView.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_, __, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router
