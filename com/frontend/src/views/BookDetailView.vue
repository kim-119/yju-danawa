<template>
  <div class="page-container py-8">
    <!-- 로딩 -->
    <div v-if="loading" class="animate-pulse">
      <div class="flex gap-6">
        <div class="skeleton w-40 h-56 rounded-xl flex-shrink-0"></div>
        <div class="flex-1 space-y-3 pt-2">
          <div class="skeleton h-7 rounded w-3/4"></div>
          <div class="skeleton h-4 rounded w-1/2"></div>
          <div class="skeleton h-4 rounded w-1/3"></div>
        </div>
      </div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-24">
      <p class="text-5xl mb-4">😕</p>
      <p class="text-gray-600 font-semibold">도서 정보를 불러올 수 없습니다.</p>
      <button @click="$router.back()" class="btn-outline mt-4">뒤로가기</button>
    </div>

    <!-- 상세 -->
    <div v-else-if="book">
      <!-- 상단: 표지 + 기본 정보 -->
      <div class="flex flex-col sm:flex-row gap-8 mb-8">
        <!-- 표지 -->
        <div class="flex-shrink-0 mx-auto sm:mx-0">
          <img
            :src="book.coverUrl || placeholder"
            :alt="book.title"
            class="w-40 sm:w-48 rounded-xl shadow-lg object-cover"
            @error="e => e.target.src = placeholder"
          />
        </div>

        <!-- 정보 -->
        <div class="flex-1">
          <h1 class="text-2xl sm:text-3xl font-black text-gray-900 leading-tight">{{ book.title }}</h1>
          <div class="mt-3 space-y-1 text-sm text-gray-600">
            <p><span class="font-medium text-gray-800">저자</span> · {{ book.author || '정보 없음' }}</p>
            <p><span class="font-medium text-gray-800">출판사</span> · {{ book.publisher || '정보 없음' }}</p>
            <p><span class="font-medium text-gray-800">ISBN-13</span> · <span class="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded">{{ book.isbn13 }}</span></p>
          </div>

          <!-- 도서관 상태 배지 -->
          <div class="mt-4 flex items-center gap-3">
            <span v-if="book.library?.holding === true">
              <span v-if="book.library?.status === 'AVAILABLE'" class="badge-available text-sm px-3 py-1">
                ✓ 대출 가능
              </span>
              <span v-else class="badge-unavailable text-sm px-3 py-1">
                {{ book.library?.statusText || '대출 중' }}
              </span>
            </span>
            <span v-else class="badge-unknown text-sm px-3 py-1">
              미소장
            </span>

            <!-- 장바구니 추가 버튼 -->
            <button
              v-if="auth.isLoggedIn"
              @click="addToCart"
              :disabled="cartAdding"
              class="inline-flex items-center gap-1.5 px-3 py-1 bg-indigo-50 text-indigo-700 hover:bg-indigo-100
                     rounded-full text-sm font-semibold transition-colors disabled:opacity-50"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
              {{ cartAdding ? '담는 중...' : '장바구니 담기' }}
            </button>
          </div>

          <!-- 구매 링크 -->
          <div v-if="book.vendors" class="mt-5 flex flex-wrap gap-2">
            <a :href="book.vendors.aladin" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center gap-1.5 px-4 py-2 bg-orange-500 hover:bg-orange-600
                      text-white text-sm font-semibold rounded-lg transition-colors">
              알라딘
            </a>
            <a :href="book.vendors.kyobo" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center gap-1.5 px-4 py-2 bg-red-600 hover:bg-red-700
                      text-white text-sm font-semibold rounded-lg transition-colors">
              교보문고
            </a>
            <a :href="book.vendors.yes24" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-700
                      text-white text-sm font-semibold rounded-lg transition-colors">
              YES24
            </a>
          </div>
        </div>
      </div>

      <!-- 📖 책 소개 (상세 정보) -->
      <div class="card p-5 mb-4">
        <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <span class="w-5 h-5 text-indigo-600">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
          </span>
          책 소개
        </h2>

        <template v-if="detailInfoLoading">
          <div class="space-y-2">
            <div class="skeleton h-4 rounded w-full"></div>
            <div class="skeleton h-4 rounded w-5/6"></div>
            <div class="skeleton h-4 rounded w-4/6"></div>
          </div>
        </template>
        <template v-else-if="detailInfo && hasDetailContent">
          <!-- 기본 정보 태그 -->
          <div class="flex flex-wrap gap-2 mb-4 text-xs">
            <span v-if="detailInfo.categoryName" class="bg-indigo-50 text-indigo-700 px-2.5 py-1 rounded-full">
              📂 {{ detailInfo.categoryName }}
            </span>
            <span v-if="detailInfo.pubDate" class="bg-gray-100 text-gray-600 px-2.5 py-1 rounded-full">
              📅 {{ detailInfo.pubDate }}
            </span>
            <span v-if="detailInfo.itemPage" class="bg-gray-100 text-gray-600 px-2.5 py-1 rounded-full">
              📄 {{ detailInfo.itemPage }}쪽
            </span>
            <span v-if="detailInfo.customerReviewRank" class="bg-yellow-50 text-yellow-700 px-2.5 py-1 rounded-full">
              ⭐ {{ detailInfo.customerReviewRank / 2 }}/5
            </span>
            <span v-if="detailInfo.priceStandard" class="bg-gray-100 text-gray-600 px-2.5 py-1 rounded-full">
              💰 정가 {{ detailInfo.priceStandard.toLocaleString() }}원
            </span>
            <span v-if="detailInfo.priceSales" class="bg-green-50 text-green-700 px-2.5 py-1 rounded-full">
              🏷️ 판매가 {{ detailInfo.priceSales.toLocaleString() }}원
            </span>
          </div>

          <!-- 부제목 -->
          <p v-if="detailInfo.subTitle" class="text-sm text-gray-500 italic mb-3">
            {{ detailInfo.subTitle }}
          </p>

          <!-- 원제 -->
          <p v-if="detailInfo.originalTitle" class="text-xs text-gray-400 mb-3">
            원제: {{ detailInfo.originalTitle }}
          </p>

          <!-- 설명 -->
          <div v-if="detailInfo.description" class="mb-4">
            <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-line"
               :class="{ 'line-clamp-5': !descExpanded }">
              {{ detailInfo.description }}
            </p>
            <button v-if="detailInfo.description.length > 200"
                    @click="descExpanded = !descExpanded"
                    class="text-xs text-blue-500 hover:text-blue-600 mt-1">
              {{ descExpanded ? '접기' : '더보기' }}
            </button>
          </div>

          <!-- 목차 -->
          <div v-if="detailInfo.toc">
            <button @click="tocExpanded = !tocExpanded"
                    class="flex items-center gap-1 text-sm font-semibold text-gray-700 hover:text-gray-900 mb-2">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 transition-transform"
                   :class="{ 'rotate-90': tocExpanded }" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
              </svg>
              목차
            </button>
            <div v-if="tocExpanded"
                 class="text-sm text-gray-600 leading-relaxed whitespace-pre-line bg-gray-50 rounded-lg p-4 max-h-80 overflow-y-auto">
              {{ detailInfo.toc }}
            </div>
          </div>

          <!-- 알라딘 링크 -->
          <a v-if="detailInfo.link"
             :href="detailInfo.link"
             target="_blank"
             rel="noopener noreferrer"
             class="inline-block mt-3 text-xs text-blue-500 hover:text-blue-600 hover:underline">
            알라딘에서 더 보기 →
          </a>
        </template>
        <p v-else class="text-sm text-gray-400">책 소개 정보가 없습니다.</p>
      </div>

      <!-- 정보 카드들 -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <!-- 도서관 대출 정보 -->
        <div class="card p-5">
          <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span class="w-5 h-5 text-blue-600">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z"/>
              </svg>
            </span>
            도서관 대출 정보
          </h2>
          <template v-if="book.library">
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between">
                <dt class="text-gray-500">소장 여부</dt>
                <dd class="font-medium" :class="book.library.holding ? 'text-green-600' : 'text-gray-500'">
                  {{ book.library.holding ? '소장' : '미소장' }}
                </dd>
              </div>
              <div v-if="book.library.holding" class="flex justify-between">
                <dt class="text-gray-500">대출 상태</dt>
                <dd class="font-medium">{{ book.library.statusText || '-' }}</dd>
              </div>
              <div v-if="book.library.location" class="flex justify-between">
                <dt class="text-gray-500">위치</dt>
                <dd>{{ book.library.location }}</dd>
              </div>
              <div v-if="book.library.callNo" class="flex justify-between">
                <dt class="text-gray-500">청구기호</dt>
                <dd class="font-mono text-xs">{{ book.library.callNo }}</dd>
              </div>
            </dl>
            <a
              v-if="book.library.detailUrl"
              :href="book.library.detailUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="btn-outline mt-4 w-full text-sm py-2"
            >
              도서관 상세보기
            </a>
          </template>
          <p v-else class="text-gray-400 text-sm">정보 없음</p>
        </div>

        <!-- 전자책 정보 -->
        <div class="card p-5">
          <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span class="w-5 h-5 text-green-600">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
            </span>
            전자책 정보
          </h2>
          <template v-if="ebookLoading">
            <div class="flex flex-col items-center justify-center py-6">
              <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-green-600 mb-2"></div>
              <p class="text-sm text-gray-400">전자책 정보 로딩 중...</p>
            </div>
          </template>
          <template v-else-if="book.ebook?.found">
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between">
                <dt class="text-gray-500">전체 권수</dt>
                <dd>{{ book.ebook.totalHoldings }}권</dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-gray-500">대출 가능</dt>
                <dd :class="book.ebook.availableHoldings > 0 ? 'text-green-600 font-medium' : 'text-red-500'">
                  {{ book.ebook.availableHoldings }}권
                </dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-gray-500">상태</dt>
                <dd>{{ book.ebook.statusText }}</dd>
              </div>
            </dl>
            <a
              v-if="book.ebook.deepLinkUrl"
              :href="book.ebook.deepLinkUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="btn-primary mt-4 w-full text-sm py-2"
            >
              전자책 대출하기
            </a>
          </template>
          <div v-else class="flex flex-col items-center justify-center py-6 text-gray-400">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 mb-2 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <p class="text-sm">전자책 정보 없음</p>
          </div>
        </div>

        <!-- 가격 비교 -->
        <div class="card p-5 md:col-span-2">
          <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span class="w-5 h-5 text-amber-600">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </span>
            가격 비교
          </h2>

          <div v-if="pricesLoading" class="flex gap-3">
            <div v-for="n in 3" :key="n" class="skeleton h-20 rounded-lg flex-1"></div>
          </div>
          <div v-else-if="filteredPrices.length > 0" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <a
              v-for="price in filteredPrices"
              :key="price.store"
              :href="price.url"
              target="_blank"
              rel="noopener noreferrer"
              class="block border rounded-xl p-4 transition-colors group"
              :class="vendorStyle(price.store).border"
            >
              <p class="text-sm font-bold transition-colors"
                 :class="vendorStyle(price.store).text">
                {{ price.storeName || vendorLabel(price.store) }}
              </p>
              <p v-if="price.price" class="text-xl font-black text-gray-900 mt-1">
                {{ price.price.toLocaleString() }}<span class="text-sm font-normal ml-0.5">원</span>
              </p>
              <p v-else class="text-sm text-gray-400 mt-1">가격 정보 없음</p>
              <p v-if="price.discountRate" class="text-xs text-red-500 mt-0.5">{{ price.discountRate }}% 할인</p>
            </a>
          </div>
          <p v-else class="text-sm text-gray-400">가격 정보를 불러올 수 없습니다.</p>
        </div>
      </div>

      <!-- 📊 체감 난이도 히트맵 -->
      <div class="card p-5 mt-4">
        <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <span class="w-5 h-5 text-indigo-500">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </span>
          독자 체감 난이도
        </h2>
        <div v-if="heatmapLoading" class="h-32 flex items-center justify-center">
          <div class="animate-pulse text-gray-400 text-sm">분석 데이터 로딩 중...</div>
        </div>
        <div v-else-if="hasHeatmapData" class="space-y-3">
          <div v-for="level in [5, 4, 3, 2, 1]" :key="level" class="flex items-center gap-3">
            <span class="w-16 text-xs text-gray-500 font-medium">{{ difficultyLabel(level) }}</span>
            <div class="flex-1 h-4 bg-gray-100 rounded-full overflow-hidden">
              <div
                class="h-full bg-indigo-500 transition-all duration-1000"
                :style="{ width: heatmapPercentage(level) + '%' }"
              ></div>
            </div>
            <span class="w-8 text-xs text-gray-400 text-right">{{ heatmap[level] || 0 }}</span>
          </div>
          <p class="text-[11px] text-gray-400 mt-2 text-center">리뷰 텍스트 키워드 분석 결과입니다.</p>
        </div>
        <div v-else class="py-8 text-center text-gray-400 text-sm">
          아직 난이도 분석 데이터가 없습니다.
        </div>
      </div>

      <!-- 💬 리뷰 섹션 (댓글 통합) -->
      <div class="card p-5 mt-4">
        <h2 class="font-bold text-gray-900 mb-4">리뷰 및 댓글</h2>

        <div v-if="reviewsLoading" class="space-y-2 mb-4">
          <div v-for="n in 3" :key="n" class="skeleton h-14 rounded-lg"></div>
        </div>
        <div v-else-if="reviews.length === 0" class="text-sm text-gray-400 mb-4">
          첫 리뷰를 남겨보세요.
        </div>
        <div v-else class="space-y-3 mb-4">
          <div v-for="r in reviews" :key="r.id" class="border border-gray-200 rounded-lg p-3">
            <div class="flex items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <span class="text-xs font-semibold text-gray-700">{{ r.username }}</span>
                <span class="text-[10px] bg-indigo-50 text-indigo-600 px-1.5 py-0.5 rounded">
                  {{ difficultyLabel(r.rating) }}
                </span>
                <span class="text-[10px] text-gray-400">{{ formatDate(r.createdAt) }}</span>
              </div>
              <button
                v-if="r.ownedByMe"
                @click="deleteReview(r.id)"
                class="text-xs text-red-500 hover:text-red-600"
              >
                삭제
              </button>
            </div>
            <p class="text-sm text-gray-800 mt-1.5 whitespace-pre-wrap">{{ r.content }}</p>
            <div class="mt-3">
              <button
                @click="toggleLike(r)"
                class="inline-flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-full border transition-all"
                :class="r.likedByMe 
                  ? 'border-pink-200 text-pink-600 bg-pink-50 font-bold' 
                  : 'border-gray-200 text-gray-500 hover:bg-gray-50'"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" :fill="r.likedByMe ? 'currentColor' : 'none'" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                좋아요 {{ r.likeCount || 0 }}
              </button>
            </div>
          </div>
        </div>

        <!-- 리뷰 작성 -->
        <div v-if="auth.isLoggedIn" class="space-y-3 pt-4 border-t border-gray-100">
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-gray-700">체감 난이도</span>
            <div class="flex gap-1">
              <button 
                v-for="lv in [1, 2, 3, 4, 5]" :key="lv"
                @click="reviewRating = lv"
                class="text-xs px-2 py-1 rounded transition-colors"
                :class="reviewRating === lv ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'"
              >
                {{ difficultyLabel(lv) }}
              </button>
            </div>
          </div>
          <textarea
            v-model="reviewInput"
            rows="3"
            maxlength="1000"
            class="w-full border border-gray-300 rounded-lg p-3 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-200"
            placeholder="도서에 대한 솔직한 리뷰를 남겨주세요."
          />
          <div class="flex items-center justify-between">
            <p class="text-xs text-gray-400">{{ reviewInput.length }}/1000</p>
            <button
              @click="submitReview"
              :disabled="reviewSubmitting || !reviewInput.trim()"
              class="btn-primary text-sm px-6 py-2 disabled:opacity-50"
            >
              {{ reviewSubmitting ? '등록 중...' : '리뷰 등록' }}
            </button>
          </div>
        </div>
        <div v-else class="text-sm text-gray-500 py-4 text-center bg-gray-50 rounded-lg">
          리뷰 작성과 좋아요는 로그인 후 이용할 수 있습니다.
        </div>
      </div>

      <button @click="$router.back()" class="btn-outline mt-6">← 뒤로가기</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'

const route    = useRoute()
const auth     = useAuthStore()
const isbn13   = route.params.isbn13
const book     = ref(null)
const loading  = ref(true)
const error    = ref(false)
const prices   = ref([])
const pricesLoading = ref(false)
const ebookLoading = ref(false)

// 리뷰/댓글 통합 상태
const reviews = ref([])
const reviewsLoading = ref(false)
const reviewInput = ref('')
const reviewRating = ref(3)
const reviewSubmitting = ref(false)

const detailInfo = ref(null)
const detailInfoLoading = ref(false)
const descExpanded = ref(false)
const tocExpanded = ref(false)
const placeholder = 'https://placehold.co/300x440?text=No+Cover'

const cartAdding = ref(false)
const heatmap = ref({})
const heatmapLoading = ref(false)

const hasHeatmapData = computed(() => {
  return Object.values(heatmap.value).some(count => count > 0)
})

function difficultyLabel(level) {
  const labels = { 5: '매우 어려움', 4: '어려움', 3: '보통', 2: '쉬움', 1: '매우 쉬움' }
  return labels[level] || '알 수 없음'
}

function heatmapPercentage(level) {
  const counts = Object.values(heatmap.value)
  const total = counts.reduce((a, b) => a + b, 0)
  if (total === 0) return 0
  return ((heatmap.value[level] || 0) / total) * 100
}

async function addToCart() {
  if (!auth.isLoggedIn) {
    alert('로그인 후 이용 가능합니다.')
    return
  }
  cartAdding.value = true
  try {
    await api.addToCart(isbn13)
    alert('장바구니에 담았습니다.')
  } catch {
    alert('장바구니 담기에 실패했습니다.')
  } finally {
    cartAdding.value = false
  }
}

async function fetchHeatmap(isbn) {
  heatmapLoading.value = true
  try {
    const { data } = await api.getDifficultyHeatmap(isbn)
    heatmap.value = data || {}
  } catch {
    heatmap.value = {}
  } finally {
    heatmapLoading.value = false
  }
}

const hasDetailContent = computed(() => {
  if (!detailInfo.value) return false
  const d = detailInfo.value
  return d.description || d.toc || d.categoryName || d.pubDate || d.itemPage || d.subTitle || d.originalTitle
})

// 알라딘, YES24, 교보문고만 고정 순서로 표시
const ALLOWED_STORES = ['aladin', 'yes24', 'kyobo']
const filteredPrices = computed(() => {
  const map = Object.fromEntries(prices.value.map(p => [p.store, p]))
  return ALLOWED_STORES.map(id => map[id]).filter(Boolean)
})

function vendorLabel(store) {
  const labels = { aladin: '알라딘', yes24: 'YES24', kyobo: '교보문고' }
  return labels[store] || store
}

function vendorStyle(store) {
  const styles = {
    aladin: {
      border: 'border-orange-200 hover:border-orange-400',
      text:   'text-orange-600 group-hover:text-orange-700'
    },
    yes24: {
      border: 'border-blue-200 hover:border-blue-400',
      text:   'text-blue-600 group-hover:text-blue-700'
    },
    kyobo: {
      border: 'border-red-200 hover:border-red-400',
      text:   'text-red-600 group-hover:text-red-700'
    }
  }
  return styles[store] || {
    border: 'border-gray-200 hover:border-yju',
    text:   'text-gray-700 group-hover:text-yju'
  }
}

async function fetchDetail() {
  loading.value = true
  error.value   = false
  try {
    const { data } = await api.getBookDetail(isbn13)
    book.value = data
    fetchPrices(isbn13, data.title)
    fetchEbook(isbn13)
    fetchReviews(isbn13)
    fetchDetailInfo(isbn13)
    fetchHeatmap(isbn13)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function fetchEbook(isbn) {
  ebookLoading.value = true
  try {
    const { data } = await api.getEbookInfo(isbn)
    if (book.value) {
      book.value = { ...book.value, ebook: data }
    }
  } catch {
  } finally {
    ebookLoading.value = false
  }
}

async function fetchDetailInfo(isbn) {
  detailInfoLoading.value = true
  try {
    const { data } = await api.getBookDetailInfo(isbn)
    detailInfo.value = data
  } catch {
    detailInfo.value = null
  } finally {
    detailInfoLoading.value = false
  }
}

async function fetchPrices(isbn, title) {
  pricesLoading.value = true
  try {
    const { data } = await api.getBookPrices(isbn, title)
    prices.value = data || []
  } catch {
    prices.value = []
  } finally {
    pricesLoading.value = false
  }
}

// 리뷰 통합 조회
async function fetchReviews(isbn) {
  reviewsLoading.value = true
  try {
    const { data } = await api.getReviews(isbn)
    reviews.value = data || []
  } catch {
    reviews.value = []
  } finally {
    reviewsLoading.value = false
  }
}

async function submitReview() {
  if (!auth.isLoggedIn) {
    alert('로그인 후 리뷰를 작성할 수 있습니다.')
    return
  }
  const content = reviewInput.value.trim()
  if (!content) return

  reviewSubmitting.value = true
  try {
    await api.createReview(isbn13, content, reviewRating.value)
    reviewInput.value = ''
    reviewRating.value = 3
    await fetchReviews(isbn13)
    await fetchHeatmap(isbn13) // 난이도 통계 갱신
  } catch {
    alert('리뷰 등록에 실패했습니다.')
  } finally {
    reviewSubmitting.value = false
  }
}

async function toggleLike(review) {
  if (!auth.isLoggedIn) {
    alert('로그인 후 좋아요를 누를 수 있습니다.')
    return
  }
  try {
    const { data } = await api.toggleReviewLike(isbn13, review.id)
    review.likedByMe = data.liked
    review.likeCount = data.likeCount
  } catch {
    alert('좋아요 처리에 실패했습니다.')
  }
}

async function deleteReview(reviewId) {
  if (!auth.isLoggedIn) return
  if (!confirm('리뷰를 삭제할까요?')) return
  try {
    await api.deleteReview(isbn13, reviewId)
    reviews.value = reviews.value.filter(r => r.id !== reviewId)
    await fetchHeatmap(isbn13) // 통계 갱신
  } catch {
    alert('리뷰 삭제에 실패했습니다.')
  }
}

function formatDate(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString()
}

onMounted(fetchDetail)
</script>
