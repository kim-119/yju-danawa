<template>
  <div class="page-container py-10 max-w-2xl">
    <h1 class="section-title mb-6">내 프로필</h1>

    <div v-if="loading" class="card p-8 animate-pulse space-y-4">
      <div class="skeleton h-4 rounded w-1/3"></div>
      <div class="skeleton h-4 rounded w-1/2"></div>
      <div class="skeleton h-4 rounded w-1/4"></div>
    </div>

    <div v-else-if="profile" class="card p-8">
      <!-- 아바타 -->
      <div class="flex items-center gap-5 mb-8">
        <div class="w-16 h-16 bg-yju rounded-full flex items-center justify-center text-white text-2xl font-black">
          {{ profile.username?.[0]?.toUpperCase() || 'U' }}
        </div>
        <div>
          <p class="text-lg font-bold text-gray-900">{{ profile.fullName || profile.username }}</p>
          <p class="text-sm text-gray-500">{{ profile.department || '학과 미입력' }}</p>
          <div class="flex gap-1.5 mt-1">
            <span v-for="role in profile.roles" :key="role"
              class="text-xs px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full font-medium">
              {{ role.replace('ROLE_', '') }}
            </span>
          </div>
        </div>
      </div>

      <dl class="space-y-4 text-sm divide-y divide-gray-100">
        <div class="flex justify-between pt-4 first:pt-0">
          <dt class="text-gray-500 font-medium">아이디</dt>
          <dd class="text-gray-900">{{ profile.username }}</dd>
        </div>
        <div class="flex justify-between pt-4">
          <dt class="text-gray-500 font-medium">이메일</dt>
          <dd class="text-gray-900">{{ profile.email || '-' }}</dd>
        </div>
        <div class="flex justify-between pt-4">
          <dt class="text-gray-500 font-medium">학번</dt>
          <dd class="text-gray-900">{{ profile.studentId || '-' }}</dd>
        </div>
        <div class="flex justify-between pt-4">
          <dt class="text-gray-500 font-medium">전화번호</dt>
          <dd class="text-gray-900">{{ profile.phone || '-' }}</dd>
        </div>
      </dl>

      <div class="mt-8 pt-6 border-t border-gray-100 flex gap-3">
        <RouterLink to="/" class="btn-outline text-sm py-2 px-4">홈으로</RouterLink>
        <button @click="logout" class="text-sm text-red-500 hover:text-red-700 font-medium transition-colors">
          로그아웃
        </button>
      </div>
    </div>

    <!-- 최근 본 도서 -->
    <div v-if="!loading && auth.isLoggedIn" class="mt-8">
      <h2 class="section-title text-xl mb-4 flex items-center gap-2">
        <span class="w-5 h-5 text-indigo-600">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </span>
        최근 본 도서
      </h2>
      <div v-if="recentBooks.length > 0" class="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <RouterLink v-for="b in recentBooks" :key="b.isbn" :to="`/books/${b.isbn}`"
                    class="card p-3 hover:ring-2 hover:ring-indigo-100 transition-all">
          <img :src="b.imageUrl || placeholder" class="w-full aspect-[3/4] object-cover rounded mb-2 shadow-sm" />
          <p class="text-xs font-bold text-gray-900 truncate">{{ b.title }}</p>
          <p class="text-[10px] text-gray-500 truncate">{{ b.author }}</p>
        </RouterLink>
      </div>
      <div v-else class="card p-10 text-center text-gray-400 text-sm">
        최근 본 도서가 없습니다.
      </div>
    </div>

    <!-- 내 장바구니 -->
    <div v-if="!loading && auth.isLoggedIn" class="mt-8">
      <h2 class="section-title text-xl mb-4 flex items-center gap-2">
        <span class="w-5 h-5 text-indigo-600">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
          </svg>
        </span>
        내 장바구니
      </h2>
      <div v-if="cartItems.length > 0" class="space-y-3">
        <div v-for="item in cartItems" :key="item.id" class="card p-4 flex gap-4 items-center">
          <img :src="item.imageUrl || placeholder" class="w-12 h-16 object-cover rounded shadow-sm" />
          <div class="flex-1 min-w-0">
            <RouterLink :to="`/books/${item.bookId}`" class="text-sm font-bold text-gray-900 hover:text-indigo-600 truncate block">
              {{ item.title }}
            </RouterLink>
            <p class="text-xs text-gray-500 truncate">{{ item.author }}</p>
            <p class="text-xs text-indigo-600 mt-1 font-medium">수량: {{ item.quantity }}</p>
          </div>
          <button @click="removeFromCart(item.bookId)" class="text-xs text-red-500 hover:text-red-700 font-medium">
            삭제
          </button>
        </div>
      </div>
      <div v-else class="card p-10 text-center text-gray-400 text-sm">
        장바구니가 비어 있습니다.
      </div>
    </div>

    <div v-else-if="!loading" class="card p-8 text-center text-gray-500">
      <p>프로필을 불러올 수 없습니다.</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'

const auth    = useAuthStore()
const router  = useRouter()
const profile = ref(null)
const loading = ref(true)
const recentBooks = ref([])
const cartItems   = ref([])
const placeholder = 'https://placehold.co/300x440?text=No+Cover'

function logout() {
  auth.logout()
  router.push('/')
}

async function removeFromCart(bookId) {
  if (!confirm('장바구니에서 삭제할까요?')) return
  try {
    const { data } = await api.removeFromCart(bookId)
    cartItems.value = data
  } catch {
    alert('삭제에 실패했습니다.')
  }
}

onMounted(async () => {
  try {
    const { data } = await api.getMyProfile()
    profile.value  = data
    
    // 추가 정보 로드
    const [recentRes, cartRes] = await Promise.allSettled([
      api.getMyRecentBooks(),
      api.getCart()
    ])
    
    if (recentRes.status === 'fulfilled') recentBooks.value = recentRes.value.data
    if (cartRes.status === 'fulfilled') cartItems.value = cartRes.value.data
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
})
</script>
