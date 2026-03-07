<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <NavBar />
    <main class="flex-1">
      <RouterView />
    </main>
    <FooterBar />
  </div>
</template>

<script setup>
import NavBar from '@/components/NavBar.vue'
import FooterBar from '@/components/FooterBar.vue'
</script>
