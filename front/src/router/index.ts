import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router';
import StudentListPage from '../pages/StudentListPage.vue';
import StudentDetailPage from '../pages/StudentDetailPage.vue';
import CourseListPage from '../pages/CourseListPage.vue';
import CourseDetailPage from '../pages/CourseDetailPage.vue';
import ModuleDesignerPage from '../pages/ModuleDesignerPage.vue';
import DynamicModuleRunnerPage from '../pages/DynamicModuleRunnerPage.vue';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    redirect: '/students'
  },
  {
    path: '/students',
    name: 'StudentList',
    component: StudentListPage,
    meta: { title: '学生综合档案' }
  },
  {
    path: '/students/:id',
    name: 'StudentDetail',
    component: StudentDetailPage,
    meta: { title: '学生全景档案详情' }
  },
  {
    path: '/courses',
    name: 'CourseList',
    component: CourseListPage,
    meta: { title: '课程排课中心' }
  },
  {
    path: '/courses/:id',
    name: 'CourseDetail',
    component: CourseDetailPage,
    meta: { title: '课程排课全景详情' }
  },
  {
    path: '/modules/designer/:moduleId?',
    name: 'ModuleDesigner',
    component: ModuleDesignerPage,
    meta: { title: '模块配置与设计中心' }
  },
  {
    path: '/modules/run/:moduleId?',
    redirect: to => `/modules/designer/${to.params.moduleId || 101}`
  }
];

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});
