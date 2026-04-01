const fs = require('fs');

let c = fs.readFileSync('src/views/files/index.vue', 'utf8');

const regex = /const handleExportCommand = async \(command\) => \{[\s\S]*?受堆内存限制[\s\S]*?\n\s*\}/;

const newFn = `const handleExportCommand = async (command) => {
    if (tabs.value.length === 0) {
      ElMessage.warning('没有可导出的数据文件！')
      return
    }
    if (command === 'zip') {
      try {
        ElMessage.info('后端正流式组装并压缩导出数据，请耐心等待...')
        const queries = tabs.value.map(tab => ({
          fileId: tab.fileId,
          filters: buildFiltersParam(tab)
        }))
        const blob = await exportBatchZipStream(queries)
        const url = window.URL.createObjectURL(new Blob([blob]))
        const link = document.createElement('a')
        link.style.display = 'none'
        link.href = url
        link.setAttribute('download', '批量档案打包.zip')
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        ElMessage.success('批量压缩包导出成功！')
      } catch (err) {
        console.error(err)
        ElMessage.error('批量压缩导出失败，请重试')
      }
    } else {
      ElMessage.warning('为防止浏览器内存溢出，直接多Sheet导出已拦截，请选择【多文件压缩包 (.zip)】通过服务侧流式构建下载！')
    }
  }`;

if (regex.test(c)) {
    c = c.replace(regex, newFn);
    
    // ensure import exists
    if (!c.includes('exportBatchZipStream')) {
        c = c.replace(/import\s*\{\s*getFileList,\s*getPageData,\s*exportFilteredExcel,\s*getFilePage\s*\}\s*from\s*'@\/api\/file'/, 
        "import { getFileList, getPageData, exportFilteredExcel, getFilePage, exportBatchZipStream } from '@/api/file'");
    }

    fs.writeFileSync('src/views/files/index.vue', c, 'utf8');
    console.log('files/index.vue updated');
} else {
    console.log('Regex not matched in files/index.vue');
}
