const fs = require('fs');
const filepath = 'src/main/java/com/xy/welllog/controller/LogDataController.java';
let code = fs.readFileSync(filepath, 'utf8');

const injection = `
    @PostMapping("/export-batch-zip")
    public void exportBatchZip(@RequestBody List<LogDataQueryDTO> queries, HttpServletResponse response) {
        try {
            response.setContentType("application/zip");
            response.setCharacterEncoding("utf-8");
            String zipName = URLEncoder.encode("批量测井数据导出", StandardCharsets.UTF_8).replaceAll("\\\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + zipName + ".zip");

            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
                for (LogDataQueryDTO query : queries) {
                    if (query.getFileId() == null) continue;

                    LogFileInfo fileInfo = fileInfoService.getById(query.getFileId());
                    if (fileInfo == null) continue;

                    String fileName = fileInfo.getFileName() != null ? fileInfo.getFileName() : ("File_" + query.getFileId() + ".txt");
                    fileName = fileName.replaceAll("[\\\\\\\\/?*:\\\\[\\\\]]", "_") + ".xlsx";

                    query.setCurrent(1);
                    query.setSize(500000);

                    Page<Map<String, Object>> pageData = pageQuery(query).getData();
                    if (pageData == null || pageData.getRecords() == null || pageData.getRecords().isEmpty()) { continue; }

                    List<String> cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
                    List<List<String>> head = new ArrayList<>();
                    for(String col : cols) {
                        head.add(Collections.singletonList(col));
                    }

                    List<List<Object>> dataList = new ArrayList<>();
                    for(Map<String, Object> map : pageData.getRecords()) {
                        List<Object> row = new ArrayList<>();
                        for(String col : cols) {
                            Object val = map.get(col);
                            if (val == null) val = map.get(col.toLowerCase());
                            if (val == null) val = "";
                            row.add(val);
                        }
                        dataList.add(row);
                    }

                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);

                    EasyExcel.write(zos)
                            .head(head)
                            .autoCloseStream(Boolean.FALSE)
                            .sheet("Filtered Data")
                            .doWrite(dataList);

                    zos.closeEntry();
                }
                zos.finish();
            }
        } catch (Exception e) {
            log.error("批量导出ZIP流异常", e);
            response.setStatus(500);
        }
    }

    @PostMapping("/{fileId}/export")
`;

code = code.replace(/@PostMapping\(\s*"\/{fileId}\/export"\s*\)/, injection);
fs.writeFileSync(filepath, code, 'utf8');
console.log('Java code injected!');
