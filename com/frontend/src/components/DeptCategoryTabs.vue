<template>
  <section class="bg-white border-t border-gray-100">
    <div class="page-container py-8">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-bold text-gray-900">학과별 중고 서적 거래</h2>
        <RouterLink
          to="/market"
          class="flex items-center gap-1 text-sm text-yju hover:text-yju-light font-medium transition-colors"
        >
          게시판 가기
          <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
          </svg>
        </RouterLink>
      </div>

      <!-- 학과 탭 (가로 스크롤 + 드래그) -->
      <div class="relative">
        <div
          ref="tabsRef"
          class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide select-none"
          :class="isDragging ? 'cursor-grabbing' : 'cursor-grab'"
          @mousedown="startDrag"
          @mousemove="onDrag"
          @mouseup="endDrag"
          @mouseleave="endDrag"
        >
          <button
            v-for="dept in departments"
            :key="dept.id"
            @click="selectDept(dept)"
            :class="[
              'flex-shrink-0 px-4 py-1.5 rounded-full text-sm font-medium transition-all whitespace-nowrap',
              selectedDept?.id === dept.id
                ? 'bg-yju text-white shadow-sm'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            ]"
          >
            {{ dept.name }}
          </button>
        </div>
      </div>

      <!-- 도서 목록 -->
      <div class="mt-5">
        <!-- 스켈레톤 -->
        <div v-if="booksLoading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          <div v-for="n in 8" :key="n" class="bg-white rounded-lg border border-gray-200/60 shadow-sm p-3">
            <div class="skeleton w-full aspect-[3/4] rounded mb-2"></div>
            <div class="skeleton h-3 rounded mb-1.5"></div>
            <div class="skeleton h-3 rounded w-2/3 mb-1.5"></div>
            <div class="skeleton h-4 rounded w-1/2"></div>
          </div>
        </div>

        <!-- 도서 카드 -->
        <div v-else-if="books.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          <RouterLink
            v-for="book in books"
            :key="book.id"
            :to="{ name: 'market-detail', params: { id: book.id } }"
            class="bg-white rounded-lg border border-gray-200/60 shadow-sm p-3 hover:shadow-md hover:border-blue-200 transition-all group"
          >
            <div class="w-full aspect-[3/4] bg-gray-50 rounded overflow-hidden mb-2">
              <img
                :src="book.imageUrl || 'https://placehold.co/120x160?text=No+Cover'"
                :alt="book.title"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                @error="e => e.target.src = 'https://placehold.co/120x160?text=No+Cover'"
                loading="lazy"
              />
            </div>
            <p class="text-xs font-bold text-gray-800 line-clamp-2 leading-tight mb-1">{{ book.title }}</p>
            <p class="text-[11px] text-gray-400 truncate mb-1.5">{{ book.author }}</p>
            <p class="text-sm font-bold text-yju">
              {{ book.priceWon ? book.priceWon.toLocaleString() + '원' : '가격 미정' }}
            </p>
          </RouterLink>
        </div>

        <!-- 빈 상태 -->
        <div v-else class="text-center py-12 text-gray-400">
          <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 mx-auto mb-3 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
              d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
          </svg>
          <p class="text-sm">
            {{ selectedDept ? `${selectedDept.name}의 중고 서적이 없습니다.` : '중고 서적이 없습니다.' }}
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'

const departments = ref([])
const selectedDept = ref(null)
const books = ref([])
const booksLoading = ref(false)
const tabsRef = ref(null)

// 드래그 스크롤
const isDragging = ref(false)
let startX = 0
let scrollLeft = 0

function startDrag(e) {
  isDragging.value = true
  startX = e.pageX - tabsRef.value.offsetLeft
  scrollLeft = tabsRef.value.scrollLeft
}

function onDrag(e) {
  if (!isDragging.value) return
  e.preventDefault()
  const x = e.pageX - tabsRef.value.offsetLeft
  tabsRef.value.scrollLeft = scrollLeft - (x - startX)
}

function endDrag() {
  isDragging.value = false
}

async function loadDepartments() {
  try {
    const { data } = await api.getDepartments()
    departments.value = data
  } catch {
    departments.value = []
  }
}

async function selectDept(dept) {
  if (selectedDept.value?.id === dept.id) return
  selectedDept.value = dept
  await fetchBooks(dept.id)
}

async function fetchBooks(deptId) {
  booksLoading.value = true
  books.value = []
  try {
    const { data } = await api.getUsedBooks(deptId)
    books.value = data.items || []
  } catch {
    books.value = []
  } finally {
    booksLoading.value = false
  }
}

onMounted(async () => {
  await loadDepartments()
  if (departments.value.length > 0) {
    await selectDept(departments.value[0])
  }
})
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
</style>
