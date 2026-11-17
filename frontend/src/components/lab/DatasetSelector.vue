<template>
  <section class="selector-card">
    <header class="card-head"><h3>选择数据集</h3></header>
    <div class="dataset-list">
      <button
        v-for="item in datasets"
        :key="item.id"
        type="button"
        class="dataset-item"
        :class="{ selected: item.id === selectedId }"
        @click="$emit('select', item.id)"
      >
        <div class="dataset-name">{{ item.name }}</div>
        <div class="dataset-info">
          <span>{{ item.pointCount }} 个样本</span>
          <span>{{ item.featureNames?.length ?? 0 }} 个特征</span>
        </div>
        <div class="dataset-desc">{{ item.description }}</div>
      </button>

      <div v-if="!datasets.length" class="empty-message">暂无可用数据集。</div>

      <div class="upload-section">
        <p class="upload-label">上传小型 CSV 数据集</p>
        <input ref="fileInput" type="file" accept=".csv" @change="onFileChange" />
        <p v-if="uploading" class="upload-status">上传解析中……</p>
        <p v-if="message" class="upload-status" :class="{ error: uploadFailed }">{{ message }}</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { uploadDataset } from '@/api/ml'

const emit = defineEmits(['select', 'uploaded'])

const fileInput = ref(null)
const uploading = ref(false)
const message = ref('')
const uploadFailed = ref(false)

async function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true
  message.value = ''
  uploadFailed.value = false
  try {
    const form = new FormData()
    form.append('file', file)
    form.append('name', file.name.replace(/\.csv$/i, ''))
    form.append('description', '上传的 CSV 数据集')
    await uploadDataset(form)
    message.value = '上传成功，已加入数据集列表'
    emit('uploaded')
  } catch (error) {
    uploadFailed.value = true
    message.value = '上传失败：' + error.message
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

defineProps({
  datasets: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null }
})

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

.dataset-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 18px;
  max-height: 380px;
  overflow-y: auto;
}

.dataset-item {
  padding: 12px 14px;
  text-align: left;
  background: #fbfcfe;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 16px;
  cursor: pointer;
  transition: all 160ms ease;
}

.dataset-item:hover {
  border-color: rgba(8, 145, 178, 0.45);
  background: #f4fbff;
}

.dataset-item.selected {
  border-color: #0891b2;
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.08), rgba(139, 92, 246, 0.06));
  box-shadow: inset 0 0 0 1px rgba(8, 145, 178, 0.25);
}

.dataset-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.dataset-info {
  display: flex;
  gap: 10px;
  margin-top: 3px;
  font-size: 11px;
  color: #0891b2;
}

.dataset-desc {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.55;
  color: #64748b;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-message {
  font-size: 12px;
  color: #94a3b8;
}

.upload-section {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(226, 232, 240, 0.9);
}

.upload-label {
  margin: 0 0 6px;
  font-size: 11px;
  color: #64748b;
}

.upload-section input[type='file'] {
  width: 100%;
  font-size: 10px;
  color: #64748b;
}

.upload-status {
  margin: 6px 0 0;
  font-size: 10px;
  color: #15803d;
}

.upload-status.error {
  color: #b91c1c;
}
</style>
