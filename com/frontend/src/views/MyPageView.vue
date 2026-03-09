<template>
  <div class="page-container py-8 max-w-3xl mx-auto space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center gap-3">
      <div class="w-10 h-10 rounded-full bg-yju flex items-center justify-center text-white font-bold text-lg">
        {{ auth.username?.charAt(0)?.toUpperCase() ?? 'U' }}
      </div>
      <div>
        <p class="font-bold text-gray-900">{{ auth.username }}</p>
        <p class="text-xs text-gray-400">마이페이지</p>
      </div>
    </div>

    <!-- 탭 -->
    <div class="flex border-b border-gray-200">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        @click="activeTab = tab.key"
        :class="[
          'px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px',
          activeTab === tab.key
            ? 'border-yju text-yju'
            : 'border-transparent text-gray-500 hover:text-gray-700'
        ]"
      >
        {{ tab.label }}
        <span v-if="tab.key === 'cart' && cartItems.length"
              class="ml-1 bg-yju text-white text-[10px] font-bold rounded-full px-1.5 py-0.5">
          {{ cartItems.length }}
        </span>
      </button>
    </div>

    <!-- ── 장바구니 탭 ── -->
    <div v-show="activeTab === 'cart'">
      <div v-if="cartLoading" class="space-y-3">
        <div v-for="n in 3" :key="n" class="h-20 bg-gray-100 rounded-xl animate-pulse" />
      </div>

      <div v-else-if="cartItems.length === 0" class="text-center py-16 text-gray-400">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
            d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.3 2.3c-.6.6-.2 1.7.7 1.7H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
        </svg>
        <p class="text-sm">장바구니가 비어있습니다.</p>
        <RouterLink to="/" class="mt-2 inline-block text-xs text-yju hover:underline">도서 검색하러 가기</RouterLink>
      </div>

      <div v-else class="space-y-3">
        <div
          v-for="item in cartItems"
          :key="item.id"
          class="flex items-center gap-3 bg-white rounded-xl border border-gray-200 p-3"
        >
          <img
            :src="item.imageUrl || 'https://placehold.co/56x80?text=No+Cover'"
            :alt="item.title"
            class="w-14 h-20 object-cover rounded flex-shrink-0"
          />
          <div class="flex-1 min-w-0">
            <RouterLink :to="{ name: 'book-detail', params: { isbn13: item.bookId } }"
                        class="font-semibold text-sm text-gray-900 hover:text-yju truncate block">
              {{ item.title ?? '(도서 정보 없음)' }}
            </RouterLink>
            <p class="text-xs text-gray-500 truncate">{{ item.author }}</p>
            <p v-if="item.price" class="text-xs font-bold text-yju mt-0.5">
              {{ (item.price * item.quantity).toLocaleString() }}원
            </p>
          </div>
          <!-- 수량 조절 -->
          <div class="flex items-center gap-1 flex-shrink-0">
            <button @click="changeQty(item, -1)"
                    class="w-7 h-7 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 text-sm font-bold">-</button>
            <span class="w-6 text-center text-sm font-semibold">{{ item.quantity }}</span>
            <button @click="changeQty(item, +1)"
                    class="w-7 h-7 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 text-sm font-bold">+</button>
          </div>
          <button @click="removeItem(item.bookId)"
                  class="ml-1 text-gray-300 hover:text-red-400 transition-colors flex-shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <!-- 합계 -->
        <div class="flex justify-between items-center bg-gray-50 rounded-xl px-4 py-3 border border-gray-200">
          <span class="text-sm text-gray-600">총 {{ cartItems.length }}종 {{ totalQty }}권</span>
          <span class="font-bold text-gray-900">{{ totalPrice.toLocaleString() }}원</span>
        </div>
      </div>
    </div>

    <!-- ── 최근 본 도서 탭 ── -->
    <div v-show="activeTab === 'recent'">
      <div v-if="recentLoading" class="grid grid-cols-3 sm:grid-cols-5 gap-3">
        <div v-for="n in 10" :key="n" class="aspect-[2/3] bg-gray-100 rounded-lg animate-pulse" />
      </div>

      <div v-else-if="recentBooks.length === 0" class="text-center py-16 text-gray-400 text-sm">
        최근 본 도서가 없습니다.
      </div>

      <div v-else class="grid grid-cols-3 sm:grid-cols-5 gap-3">
        <RouterLink
          v-for="book in recentBooks"
          :key="book.isbn"
          :to="{ name: 'book-detail', params: { isbn13: book.isbn } }"
          class="group"
        >
          <img
            :src="book.imageUrl || 'https://placehold.co/120x174?text=No+Cover'"
            :alt="book.title"
            class="w-full aspect-[2/3] object-cover rounded-lg shadow-sm group-hover:shadow-md transition-shadow"
          />
          <p class="mt-1.5 text-xs text-gray-700 truncate leading-tight">{{ book.title }}</p>
          <p class="text-[10px] text-gray-400 truncate">{{ book.author }}</p>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const tabs = [
  { key: 'cart',   label: '장바구니' },
  { key: 'recent', label: '최근 본 도서' }
]
const activeTab = ref('cart')

// ── 장바구니 ──
const cartItems = ref([])
const cartLoading = ref(true)

const totalQty   = computed(() => cartItems.value.reduce((s, i) => s + i.quantity, 0))
const totalPrice = computed(() => cartItems.value.reduce((s, i) => s + (i.price ?? 0) * i.quantity, 0))

async function fetchCart() {
  cartLoading.value = true
  try {
    const { data } = await api.getCart()
    cartItems.value = data
  } catch { cartItems.value = [] }
  finally { cartLoading.value = false }
}

async function changeQty(item, delta) {
  const newQty = item.quantity + delta
  if (newQty <= 0) { removeItem(item.bookId); return }
  // 낙관적 UI: 즉시 반영
  item.quantity = newQty
  try {
    const { data } = await api.updateCartItemQuantity(item.bookId, newQty)
    cartItems.value = data
  } catch {
    // 롤백
    item.quantity -= delta
  }
}

async function removeItem(bookId) {
  // 낙관적 UI: 즉시 제거
  const prev = [...cartItems.value]
  cartItems.value = cartItems.value.filter(i => i.bookId !== bookId)
  try {
    const { data } = await api.removeFromCart(bookId)
    cartItems.value = data
  } catch {
    cartItems.value = prev
  }
}

// ── 최근 본 도서 ──
const recentBooks = ref([])
const recentLoading = ref(true)

async function fetchRecent() {
  recentLoading.value = true
  try {
    const { data } = await api.getMyRecentBooks()
    recentBooks.value = data
  } catch { recentBooks.value = [] }
  finally { recentLoading.value = false }
}

onMounted(() => {
  fetchCart()
  fetchRecent()
})
</script>
