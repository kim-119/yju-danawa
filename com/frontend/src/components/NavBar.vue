<template>
  <header class="bg-yju shadow-md sticky top-0 z-50">
    <div class="page-container">
      <div class="flex items-center justify-between h-16">
        <!-- 로고 -->
        <RouterLink to="/" class="flex items-center gap-2 select-none">
          <span class="text-yju-accent font-black text-xl tracking-tight">Y</span>
          <span class="text-white font-bold text-lg tracking-tight">다나와</span>
          <span class="hidden sm:inline text-blue-200 text-xs ml-1 font-normal">도서 검색</span>
        </RouterLink>

        <!-- 검색바 (데스크탑) -->
        <form @submit.prevent="doSearch" class="hidden md:flex flex-1 max-w-xl mx-8">
          <div class="relative w-full">
            <input
              v-model="keyword"
              type="search"
              placeholder="도서명, 저자, ISBN 검색..."
              class="w-full pl-4 pr-12 py-2 rounded-lg text-gray-900 text-sm
                     focus:outline-none focus:ring-2 focus:ring-yju-accent/60 border-0"
            />
            <button
              type="submit"
              class="absolute right-1 top-1/2 -translate-y-1/2 bg-yju-accent hover:bg-amber-400
                     text-white rounded-md px-3 py-1.5 text-sm font-medium transition-colors"
            >
              검색
            </button>
          </div>
        </form>

        <!-- 중고 마켓 링크 -->
        <RouterLink
          to="/market"
          class="hidden sm:flex items-center gap-1 text-blue-100 hover:text-white text-sm px-2 py-1.5 rounded-lg hover:bg-white/10 transition-colors"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
          </svg>
          중고 마켓
        </RouterLink>

        <!-- 우측 메뉴 -->
        <nav class="flex items-center gap-1 sm:gap-2">
          <template v-if="auth.isLoggedIn">
            <!-- 장바구니 아이콘 -->
            <RouterLink
              to="/my"
              class="relative flex items-center text-blue-100 hover:text-white p-1.5 rounded-lg hover:bg-white/10 transition-colors"
              title="장바구니 / 마이페이지"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.3 2.3c-.6.6-.2 1.7.7 1.7H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
              </svg>
              <span v-if="cartCount > 0"
                    class="absolute -top-0.5 -right-0.5 w-4 h-4 bg-yju-accent text-white text-[9px] font-bold
                           rounded-full flex items-center justify-center leading-none">
                {{ cartCount > 9 ? '9+' : cartCount }}
              </span>
            </RouterLink>
            <!-- 프로필 -->
            <RouterLink
              to="/my"
              class="flex items-center gap-1.5 text-blue-100 hover:text-white text-sm px-2 py-1.5 rounded-lg
                     hover:bg-white/10 transition-colors"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M5.121 17.804A13.937 13.937 0 0112 16c2.5 0 4.847.655 6.879 1.804M15 10a3 3 0 11-6 0 3 3 0 016 0z"/>
              </svg>
              <span class="hidden sm:inline">{{ auth.username }}</span>
            </RouterLink>
            <button
              @click="logout"
              class="text-blue-200 hover:text-white text-sm px-2 py-1.5 rounded-lg hover:bg-white/10 transition-colors"
            >
              로그아웃
            </button>
          </template>
          <template v-else>
            <RouterLink
              to="/login"
              class="text-blue-100 hover:text-white text-sm px-3 py-1.5 rounded-lg hover:bg-white/10 transition-colors"
            >
              로그인
            </RouterLink>
            <RouterLink
              to="/register"
              class="bg-yju-accent hover:bg-amber-400 text-white text-sm font-medium px-3 py-1.5 rounded-lg transition-colors"
            >
              회원가입
            </RouterLink>
          </template>
        </nav>
      </div>

      <!-- 모바일 검색바 -->
      <div class="md:hidden pb-3">
        <form @submit.prevent="doSearch">
          <div class="relative">
            <input
              v-model="keyword"
              type="search"
              placeholder="도서명, 저자, ISBN 검색..."
              class="w-full pl-4 pr-16 py-2 rounded-lg text-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-yju-accent/60"
            />
            <button
              type="submit"
              class="absolute right-1 top-1/2 -translate-y-1/2 bg-yju-accent hover:bg-amber-400
                     text-white rounded-md px-3 py-1.5 text-sm font-medium transition-colors"
            >
              검색
            </button>
          </div>
        </form>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'

const auth    = useAuthStore()
const router  = useRouter()
const route   = useRoute()
const keyword = ref(route.query.q || '')
const cartCount = ref(0)

async function fetchCartCount() {
  if (!auth.isLoggedIn) { cartCount.value = 0; return }
  try {
    const { data } = await api.getCart()
    cartCount.value = Array.isArray(data) ? data.length : 0
  } catch {
    cartCount.value = 0
  }
}

onMounted(fetchCartCount)
// 로그인 상태 변화 및 라우트 이동 시 갱신
watch(() => auth.isLoggedIn, fetchCartCount)
watch(() => route.path, fetchCartCount)

function doSearch() {
  if (!keyword.value.trim()) return
  router.push({ name: 'search', query: { q: keyword.value.trim() } })
}

function logout() {
  auth.logout()
  cartCount.value = 0
  router.push('/')
}
</script>
