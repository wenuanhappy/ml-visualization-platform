<template>
  <section class="selector-card">
    <header class="card-head"><h3>选择算法</h3></header>
    <div class="algorithm-list">
      <button
        v-for="item in algorithms"
        :key="item.id"
        type="button"
        class="algorithm-item"
        :class="{ selected: item.id === selectedId }"
        @click="$emit('select', item.id)"
      >
        <div class="algorithm-name">{{ item.displayName }}</div>
        <div class="algorithm-type">{{ item.type }}</div>
        <div class="algorithm-desc">{{ item.description }}</div>
      </button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  algorithms: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' }
})

defineEmits(['select'])
</script>

<style scoped>
.selector-card {
  border-radius: 24px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.14);
  overflow: hidden;
}

.card-head {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.5);
}

.card-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.algorithm-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 18px;
  max-height: 420px;
  overflow-y: auto;
}

.algorithm-item {
  padding: 12px 14px;
  text-align: left;
  background: #fbfcfe;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 16px;
  cursor: pointer;
  transition: all 160ms ease;
}

.algorithm-item:hover {
  border-color: rgba(8, 145, 178, 0.45);
  background: #f4fbff;
}

.algorithm-item.selected {
  border-color: #0891b2;
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.08), rgba(139, 92, 246, 0.06));
  box-shadow: inset 0 0 0 1px rgba(8, 145, 178, 0.25);
}

.algorithm-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.algorithm-type {
  margin-top: 2px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0891b2;
}

.algorithm-desc {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.55;
  color: #64748b;
}
</style>
