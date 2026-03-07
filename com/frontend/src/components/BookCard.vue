<template>
  <component
    :is="book.isbn13 ? 'RouterLink' : 'div'"
    :to="book.isbn13 ? { name: 'book-detail', params: { isbn13: book.isbn13 } } : undefined"
    class="card flex gap-3 p-3 hover:shadow-md hover:border-blue-200 transition-all duration-200 group"
    :class="{ 'opacity-60 cursor-not-allowed': !book.isbn13 }"
  >
    <!-- 표지 -->
    <div class="flex-shrink-0 w-16 h-24 bg-gray-100 rounded overflow-hidden">
      <img
        :src="book.thumbUrl || placeholderImg"
        :alt="book.title"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
        @error="onImgError"
        loading="lazy"
      />
    </div>

    <!-- 정보 -->
    <div class="flex-1 min-w-0 flex flex-col justify-between py-0.5">
      <div>
        <h3 class="text-sm font-semibold text-gray-900 leading-snug line-clamp-2 group-hover:text-yju transition-colors">
          {{ book.title }}
        </h3>
        <p class="text-xs text-gray-500 mt-1 truncate">{{ book.author }}</p>
        <p class="text-xs text-gray-400 truncate">{{ book.publisher }}</p>
      </div>
      <div class="flex items-center gap-1.5 mt-1">
        <span v-if="book.isbn13" class="text-xs bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded font-mono">
          {{ book.isbn13 }}
        </span>
      </div>
    </div>
  </component>
</template>

<script setup>
const PLACEHOLDER = 'https://placehold.co/120x174?text=No+Cover'

defineProps({
  book: { type: Object, required: true }
})

const placeholderImg = PLACEHOLDER

function onImgError(e) {
  e.target.src = PLACEHOLDER
}
</script>
