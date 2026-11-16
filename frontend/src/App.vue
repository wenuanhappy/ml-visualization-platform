<template>
  <div class="app-shell">
    <header class="app-header">
      <div>
        <span class="brand-kicker">高级 WEB 期末项目</span>
        <h1 class="brand-title">机器学习指导仪表板</h1>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #eef2f9;
}

.app-header {
  padding: 18px 32px 22px;
  background: linear-gradient(120deg, #3d4a63 0%, #4a5a76 60%, #56688a 100%);
  color: #fff;
}

.brand-kicker {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  opacity: 0.78;
}

.brand-title {
  margin: 4px 0 0;
  font-size: 26px;
  font-weight: 700;
}

.app-main {
  max-width: 1400px;
  margin: 0 auto;
  padding: 22px 28px 40px;
}
</style>
