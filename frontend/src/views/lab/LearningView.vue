<template>
  <div class="learning-page">
    <aside class="course-sidebar">
      <a class="sidebar-home" @click.prevent="openLessons">
        <span class="home-mark">ML</span>
        <span><strong>机器学习教程</strong><small>循序渐进 · 边学边练</small></span>
      </a>

      <div class="progress-card">
        <div class="progress-caption"><span>学习进度</span><strong>{{ progress }}%</strong></div>
        <div class="progress-track"><span :style="{ width: `${progress}%` }"></span></div>
        <small>{{ completed.size }} / {{ lessons.length }} 节已完成</small>
      </div>

      <nav v-if="view === 'lesson'" class="lesson-nav">
        <template v-for="(lesson, index) in lessons" :key="lesson.id">
          <div v-if="index === 0 || lessons[index - 1].category !== lesson.category" class="nav-group">
            {{ lesson.category }}
          </div>
          <button type="button" class="lesson-link" :class="{ selected: activeId === lesson.id }" @click="selectLesson(lesson.id)">
            <span class="lesson-number">{{ completed.has(lesson.id) ? '✓' : String(index + 1).padStart(2, '0') }}</span>
            <span>{{ lesson.title }}</span>
          </button>
        </template>
      </nav>

      <div class="study-tools">
        <div class="nav-group">考核与复习</div>
        <button type="button" class="tool-link" :class="{ selected: view === 'lesson' }" @click="openLessons">
          <span class="tool-icon">文</span><span>理论课程</span>
        </button>
        <button type="button" class="tool-link" :class="{ selected: view === 'quiz' }" @click="startQuiz()">
          <span class="tool-icon">✓</span><span>理论考核</span>
        </button>
        <button type="button" class="tool-link" :class="{ selected: view === 'wrongbook' }" @click="openWrongbook">
          <span class="tool-icon">≡</span><span>错题集</span>
          <span class="wrong-count">{{ wrongQuestionIds.length }}</span>
        </button>
      </div>

      <router-link class="sidebar-practice" to="/"><span>↗</span> 前往训练中心</router-link>
    </aside>

    <main class="lesson-main">
      <!-- 课程 -->
      <template v-if="view === 'lesson'">
        <div class="breadcrumbs">
          <span>课程</span><span>/</span><span>{{ activeLesson.category }}</span><span>/</span>
          <strong>{{ activeLesson.title }}</strong>
        </div>

        <section class="lesson-heading">
          <div class="eyebrow">
            <span class="eyebrow-dot"></span>机器学习基础课程
            <span class="duration">◷ {{ activeLesson.duration }}</span>
          </div>
          <h1>{{ activeLesson.title }}</h1>
          <p>{{ activeLesson.summary }}</p>
        </section>

        <div class="lesson-content">
          <section class="article-section">
            <h2>本节内容</h2>
            <p v-for="paragraph in activeLesson.paragraphs" :key="paragraph">{{ paragraph }}</p>
          </section>

          <section class="concept-sections">
            <article v-for="(section, index) in activeLesson.sections" :key="section.title" class="concept-section">
              <div class="concept-index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div>
                <h3>{{ section.title }}</h3>
                <p>{{ section.body }}</p>
              </div>
            </article>
          </section>

          <section class="example-card">
            <div class="example-label"><span>✳</span> 概念示例</div>
            <h3>{{ activeLesson.exampleTitle }}</h3>
            <p>{{ activeLesson.example }}</p>
            <router-link to="/" class="example-link">前往训练中心试一试 <span>→</span></router-link>
          </section>

          <section class="keypoints">
            <h2>记住这几点</h2>
            <ul>
              <li v-for="point in activeLesson.keyPoints" :key="point"><span class="check">✓</span>{{ point }}</li>
            </ul>
          </section>

          <section class="self-check">
            <div class="self-check-label">学完自测</div>
            <h2>{{ activeLesson.checkQuestion }}</h2>
            <div class="self-check-options">
              <button
                v-for="(option, index) in activeLesson.checkOptions"
                :key="option"
                type="button"
                class="self-check-option"
                :class="{
                  chosen: lessonCheckChoice === index,
                  'answer-correct': lessonCheckSubmitted && index === activeLesson.checkAnswerIndex,
                  'answer-wrong': lessonCheckSubmitted && lessonCheckChoice === index && index !== activeLesson.checkAnswerIndex
                }"
                :disabled="lessonCheckSubmitted"
                @click="lessonCheckChoice = index"
              >
                <span class="option-letter">{{ ['A', 'B', 'C', 'D'][index] }}</span><span>{{ option }}</span>
              </button>
            </div>
            <div class="self-check-actions">
              <button v-if="!lessonCheckSubmitted" class="answer-toggle" type="button" :disabled="lessonCheckChoice === null" @click="submitLessonCheck">
                检查答案 →
              </button>
              <button v-else class="answer-toggle" type="button" @click="resetLessonCheck">再试一次 ↻</button>
            </div>
            <div
              v-if="lessonCheckSubmitted"
              class="self-check-feedback"
              :class="{ 'is-correct': lessonCheckChoice === activeLesson.checkAnswerIndex }"
            >
              <strong>{{ lessonCheckChoice === activeLesson.checkAnswerIndex ? '回答正确' : '再复习一下这个知识点' }}</strong>
              <p>{{ activeLesson.checkAnswer }}</p>
            </div>
          </section>

          <div class="lesson-actions">
            <button class="previous-button" type="button" :disabled="activeIndex === 0" @click="goTo(-1)">← 上一节</button>
            <button class="complete-button" type="button" @click="markComplete">
              {{ completed.has(activeId) ? '✓ 已完成' : '标记为已完成' }}
            </button>
            <button class="next-button" type="button" :disabled="activeIndex === lessons.length - 1" @click="goTo(1)">
              下一节 →
            </button>
          </div>
        </div>

        <footer class="lesson-footer">
          <span>机器学习，从理解数据开始。</span><span>{{ activeIndex + 1 }} / {{ lessons.length }}</span>
        </footer>
      </template>

      <!-- 考核 -->
      <template v-else-if="view === 'quiz'">
        <div class="breadcrumbs"><span>学习中心</span><span>/</span><strong>理论考核</strong></div>
        <section class="assessment-heading">
          <div class="eyebrow"><span class="eyebrow-dot"></span>章节知识检查</div>
          <h1>理论考核</h1>
          <p>
            从 {{ questionCount }} 道课程题目中随机抽取 5 道单选题。题目覆盖理论课程各章节与知识点，每题 20 分，满分 100 分。提交后会显示得分，并为答错的题目提供解析、自动加入错题集。
          </p>
          <div class="exam-meta">
            <span>◉ {{ questionCount }} 道题库</span><span>每次 5 道单选题</span><span>◷ 不限时</span><span>满分 100 分</span>
          </div>
        </section>

        <section class="quiz-list">
          <article
            v-for="(question, index) in currentQuizQuestions"
            :key="question.id"
            class="quiz-card"
            :class="{
              'correct-card': quizSubmitted && quizAnswers[question.id] === question.answerIndex,
              'wrong-card': quizSubmitted && quizAnswers[question.id] !== question.answerIndex
            }"
          >
            <div class="question-meta">
              <span>第 {{ index + 1 }} 题</span><span>{{ lessonTitle(question.lessonId) }}</span><span>20 分</span>
            </div>
            <h2>{{ question.question }}</h2>
            <div class="knowledge-point">对应课程知识点：{{ question.knowledgePoint }}</div>
            <div class="option-list">
              <button
                v-for="(option, optionIndex) in question.options"
                :key="option"
                type="button"
                class="option"
                :class="{
                  chosen: quizAnswers[question.id] === optionIndex,
                  'option-correct': quizSubmitted && optionIndex === question.answerIndex,
                  'option-wrong': quizSubmitted && quizAnswers[question.id] === optionIndex && optionIndex !== question.answerIndex
                }"
                :disabled="quizSubmitted"
                @click="chooseOption(question.id, optionIndex)"
              >
                <span class="option-letter">{{ ['A', 'B', 'C', 'D'][optionIndex] }}</span><span>{{ option }}</span>
              </button>
            </div>
            <div v-if="quizSubmitted && quizAnswers[question.id] !== question.answerIndex" class="question-feedback">
              <strong>正确答案：{{ ['A', 'B', 'C', 'D'][question.answerIndex] }}. {{ question.options[question.answerIndex] }}</strong>
              <p>{{ question.explanation }}</p>
              <small>已加入错题集</small>
            </div>
            <div v-if="quizSubmitted && quizAnswers[question.id] === question.answerIndex" class="question-feedback is-correct">
              <strong>回答正确</strong><p>{{ question.explanation }}</p>
            </div>
            <button class="lesson-review-link" type="button" @click="openLesson(question.lessonId)">
              回到课程复习：{{ lessonTitle(question.lessonId) }} →
            </button>
          </article>
        </section>

        <section v-if="quizSubmitted" class="score-panel">
          <div>
            <span>本次得分</span>
            <strong>{{ quizScore }}<small> / 100</small></strong>
            <p>答对 {{ (quizScore || 0) / 20 }} 题 · 错题已保存，可随时在错题集中复习</p>
          </div>
          <div class="score-actions">
            <button class="complete-button" type="button" @click="openLessons">返回理论课程</button>
            <button class="next-button" type="button" @click="startQuiz()">再做一组</button>
          </div>
        </section>

        <div v-if="!quizSubmitted" class="quiz-actions">
          <span>已完成 {{ answeredCount }} / 5 题</span>
          <button class="next-button" type="button" :disabled="answeredCount !== 5" @click="submitQuiz">提交考核</button>
        </div>
      </template>

      <!-- 错题集 -->
      <template v-else>
        <div class="breadcrumbs"><span>学习中心</span><span>/</span><strong>错题集</strong></div>
        <section class="assessment-heading">
          <div class="eyebrow"><span class="eyebrow-dot"></span>持续复习</div>
          <h1>错题集</h1>
          <p>考核中答错的题目会保存在这台设备上，方便之后回顾知识点。完成一次新考核后，错题会自动更新到这里。</p>
          <div class="exam-meta"><span>当前收录 {{ wrongQuestions.length }} 道错题</span><span>自动保存于本机</span></div>
        </section>

        <div v-if="!wrongQuestions.length" class="empty-wrongbook">
          <span>✓</span>
          <h2>暂时没有错题</h2>
          <p>完成理论考核后，答错的题目和解析会自动收录在这里。</p>
          <button class="next-button" type="button" @click="startQuiz()">开始考核</button>
        </div>

        <section v-else class="wrongbook-list">
          <article v-for="(question, index) in wrongQuestions" :key="question.id" class="quiz-card wrong-card">
            <div class="question-meta"><span>错题 {{ index + 1 }}</span><span>{{ lessonTitle(question.lessonId) }}</span></div>
            <h2>{{ question.question }}</h2>
            <div class="knowledge-point">对应课程知识点：{{ question.knowledgePoint }}</div>
            <div class="answer-key">
              <strong>正确答案：{{ ['A', 'B', 'C', 'D'][question.answerIndex] }}. {{ question.options[question.answerIndex] }}</strong>
              <p>{{ question.explanation }}</p>
            </div>
            <button class="lesson-review-link" type="button" @click="openLesson(question.lessonId)">
              回到课程复习：{{ lessonTitle(question.lessonId) }} →
            </button>
          </article>
          <button class="next-button review-button" type="button" @click="startQuiz(true)">抽取错题练习</button>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { LESSONS } from '@/services/lessons'
import { createQuestionBank } from '@/services/questionBank'

defineOptions({ name: 'LearningView' })

const STORAGE_KEY = 'ml-learning-wrong-questions'

const lessons = LESSONS
const questionBank = createQuestionBank()

const view = ref('lesson')
const activeId = ref(lessons[0].id)
const completed = ref(new Set())
const lessonCheckChoice = ref(null)
const lessonCheckSubmitted = ref(false)

const currentQuizQuestions = ref([])
const quizAnswers = ref({})
const quizSubmitted = ref(false)
const quizScore = ref(null)
const wrongQuestionIds = ref([])

const activeIndex = computed(() => Math.max(0, lessons.findIndex(item => item.id === activeId.value)))
const activeLesson = computed(() => lessons[activeIndex.value] || lessons[0])
const progress = computed(() => Math.round((completed.value.size / lessons.length) * 100))
const questionCount = computed(() => questionBank.length)
const answeredCount = computed(() =>
  currentQuizQuestions.value.filter(question => quizAnswers.value[question.id] !== undefined).length
)
const wrongQuestions = computed(() =>
  questionBank.filter(question => wrongQuestionIds.value.includes(question.id))
)

function lessonTitle(lessonId) {
  return lessons.find(item => item.id === lessonId)?.title ?? ''
}

function selectLesson(id) {
  activeId.value = id
  view.value = 'lesson'
  lessonCheckChoice.value = null
  lessonCheckSubmitted.value = false
}

function openLesson(id) {
  selectLesson(id)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openLessons() {
  view.value = 'lesson'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goTo(offset) {
  const next = activeIndex.value + offset
  if (next >= 0 && next < lessons.length) {
    selectLesson(lessons[next].id)
  }
}

function markComplete() {
  completed.value = new Set([...completed.value, activeId.value])
}

function submitLessonCheck() {
  if (lessonCheckChoice.value === null) {
    return
  }
  lessonCheckSubmitted.value = true
}

function resetLessonCheck() {
  lessonCheckChoice.value = null
  lessonCheckSubmitted.value = false
}

function startQuiz(fromWrongbook = false) {
  const pool = fromWrongbook && wrongQuestions.value.length
    ? wrongQuestions.value
    : questionBank
  const shuffled = [...pool].sort(() => Math.random() - 0.5)
  currentQuizQuestions.value = shuffled.slice(0, Math.min(5, shuffled.length))
  quizAnswers.value = {}
  quizSubmitted.value = false
  quizScore.value = null
  view.value = 'quiz'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function chooseOption(questionId, optionIndex) {
  if (quizSubmitted.value) {
    return
  }
  quizAnswers.value = { ...quizAnswers.value, [questionId]: optionIndex }
}

function submitQuiz() {
  if (answeredCount.value !== currentQuizQuestions.value.length) {
    return
  }
  quizSubmitted.value = true
  const wrong = currentQuizQuestions.value.filter(
    question => quizAnswers.value[question.id] !== question.answerIndex
  )
  quizScore.value = (currentQuizQuestions.value.length - wrong.length) * 20

  const ids = new Set(wrongQuestionIds.value)
  wrong.forEach(question => ids.add(question.id))
  wrongQuestionIds.value = [...ids]
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(wrongQuestionIds.value))
  } catch (error) {
    // 存储不可用时保持当前会话可用
  }
}

function openWrongbook() {
  view.value = 'wrongbook'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      wrongQuestionIds.value = JSON.parse(stored) ?? []
    }
  } catch (error) {
    wrongQuestionIds.value = []
  }
})
</script>

<style scoped>
.learning-page {
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

@media (max-width: 1024px) {
  .learning-page {
    grid-template-columns: 1fr;
  }
}

.course-sidebar {
  position: sticky;
  top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.1);
}

.sidebar-home {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.home-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #3b6cf6, #6f5cf0);
  border-radius: 10px;
}

.sidebar-home strong {
  display: block;
  font-size: 13px;
  color: #0f172a;
}

.sidebar-home small {
  font-size: 10px;
  color: #94a3b8;
}

.progress-card {
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 12px;
}

.progress-caption {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
}

.progress-track {
  height: 6px;
  background: rgba(148, 163, 184, 0.2);
  border-radius: 999px;
  overflow: hidden;
}

.progress-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #378add, #38bdf8);
}

.progress-card small {
  display: block;
  margin-top: 6px;
  font-size: 10px;
  color: #94a3b8;
}

.nav-group {
  margin: 8px 0 4px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.lesson-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 340px;
  overflow-y: auto;
}

.lesson-link,
.tool-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  font-size: 12px;
  color: #475569;
  background: transparent;
  border: none;
  border-radius: 9px;
  text-align: left;
  cursor: pointer;
}

.lesson-link:hover,
.tool-link:hover {
  background: #f1f5f9;
}

.lesson-link.selected,
.tool-link.selected {
  color: #1d4ed8;
  background: #eef4ff;
  font-weight: 600;
}

.lesson-number {
  font-size: 10px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.wrong-count {
  margin-left: auto;
  padding: 1px 6px;
  font-size: 10px;
  color: #b91c1c;
  background: #fee2e2;
  border-radius: 999px;
}

.sidebar-practice {
  padding: 9px 10px;
  font-size: 12px;
  color: #1d4ed8;
  text-decoration: none;
  background: #eef4ff;
  border-radius: 10px;
  text-align: center;
}

.lesson-main {
  min-width: 0;
}

.breadcrumbs {
  display: flex;
  gap: 6px;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.breadcrumbs strong {
  color: #475569;
}

.lesson-heading h1 {
  margin: 6px 0 8px;
  font-size: 26px;
  color: #0f172a;
}

.lesson-heading > p {
  margin: 0 0 18px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.eyebrow {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #378add;
}

.eyebrow-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #378add;
}

.duration {
  margin-left: auto;
  color: #94a3b8;
  font-weight: 400;
  text-transform: none;
}

.lesson-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lesson-content section,
.self-check,
.lesson-actions {
  padding: 18px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.07);
}

.article-section h2,
.keypoints h2 {
  margin: 0 0 10px;
  font-size: 15px;
  color: #0f172a;
}

.article-section p {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.concept-sections {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.concept-section {
  display: flex;
  gap: 12px;
  padding: 16px 18px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.06);
}

.concept-index {
  font-size: 11px;
  font-weight: 700;
  color: #378add;
}

.concept-section h3 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #0f172a;
}

.concept-section p {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #64748b;
}

.example-card {
  background: #f7fbff !important;
  border: 1px solid rgba(55, 138, 221, 0.18);
}

.example-label {
  font-size: 11px;
  font-weight: 600;
  color: #378add;
  margin-bottom: 8px;
}

.example-card h3 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #0f172a;
}

.example-card p {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.8;
  color: #475569;
}

.example-link {
  font-size: 12px;
  color: #1d4ed8;
  text-decoration: none;
}

.keypoints ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.keypoints li {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #475569;
}

.check {
  color: #10b981;
}

.self-check-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 6px;
}

.self-check h2 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #0f172a;
}

.self-check-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.self-check-option,
.option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  font-size: 12px;
  text-align: left;
  color: #334155;
  background: #f8fafc;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
}

.self-check-option:hover,
.option:hover {
  border-color: rgba(55, 138, 221, 0.4);
}

.self-check-option.chosen,
.option.chosen {
  border-color: #378add;
  background: #eef6ff;
}

.self-check-option.answer-correct,
.option.option-correct {
  border-color: #10b981;
  background: #ecfdf5;
}

.self-check-option.answer-wrong,
.option.option-wrong {
  border-color: #ef4444;
  background: #fef2f2;
}

.option-letter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: #94a3b8;
  border-radius: 50%;
}

.self-check-actions {
  margin-top: 12px;
}

.answer-toggle {
  padding: 7px 14px;
  font-size: 12px;
  color: #fff;
  background: linear-gradient(120deg, #3b6cf6, #6f5cf0);
  border: none;
  border-radius: 9px;
  cursor: pointer;
}

.answer-toggle:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.self-check-feedback {
  margin-top: 12px;
  padding: 12px 14px;
  background: #fef2f2;
  border-radius: 10px;
}

.self-check-feedback.is-correct {
  background: #ecfdf5;
}

.self-check-feedback strong {
  font-size: 12px;
  color: #0f172a;
}

.self-check-feedback p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.lesson-actions {
  display: flex;
  gap: 10px;
}

.previous-button,
.next-button,
.complete-button {
  padding: 8px 14px;
  font-size: 12px;
  border-radius: 9px;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
}

.complete-button {
  color: #047857;
  border-color: rgba(16, 185, 129, 0.4);
  background: #ecfdf5;
}

.next-button {
  margin-left: auto;
  color: #fff;
  background: linear-gradient(120deg, #3b6cf6, #6f5cf0);
  border: none;
}

.previous-button:disabled,
.next-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.lesson-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  font-size: 11px;
  color: #94a3b8;
}

.assessment-heading {
  padding: 20px 22px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.07);
  margin-bottom: 16px;
}

.assessment-heading h1 {
  margin: 6px 0 8px;
  font-size: 24px;
  color: #0f172a;
}

.assessment-heading p {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.8;
  color: #64748b;
}

.exam-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 11px;
  color: #94a3b8;
}

.quiz-list,
.wrongbook-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quiz-card {
  padding: 16px 18px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.06);
}

.quiz-card.correct-card {
  border-left: 3px solid #10b981;
}

.quiz-card.wrong-card {
  border-left: 3px solid #ef4444;
}

.question-meta {
  display: flex;
  gap: 12px;
  font-size: 10px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.quiz-card h2 {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.6;
  color: #0f172a;
}

.knowledge-point {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-feedback {
  margin-top: 10px;
  padding: 10px 12px;
  background: #fef2f2;
  border-radius: 10px;
}

.question-feedback.is-correct {
  background: #ecfdf5;
}

.question-feedback strong {
  font-size: 12px;
  color: #0f172a;
}

.question-feedback p {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: #475569;
}

.question-feedback small {
  display: block;
  margin-top: 6px;
  font-size: 10px;
  color: #b91c1c;
}

.lesson-review-link {
  margin-top: 10px;
  font-size: 11px;
  color: #1d4ed8;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.score-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.07);
}

.score-panel span {
  font-size: 11px;
  color: #64748b;
}

.score-panel strong {
  display: block;
  font-size: 26px;
  color: #0f172a;
}

.score-panel small {
  font-size: 12px;
  color: #94a3b8;
}

.score-panel p {
  margin: 4px 0 0;
  font-size: 11px;
  color: #94a3b8;
}

.score-actions {
  display: flex;
  gap: 8px;
}

.quiz-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  margin-top: 14px;
  font-size: 11px;
  color: #64748b;
}

.empty-wrongbook {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.07);
}

.empty-wrongbook > span {
  font-size: 26px;
  color: #10b981;
}

.empty-wrongbook h2 {
  margin: 0;
  font-size: 15px;
  color: #0f172a;
}

.empty-wrongbook p {
  margin: 0 0 8px;
  font-size: 12px;
  color: #94a3b8;
}

.answer-key {
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 10px;
}

.answer-key strong {
  font-size: 12px;
  color: #0f172a;
}

.answer-key p {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: #475569;
}

.review-button {
  align-self: flex-start;
}
</style>
