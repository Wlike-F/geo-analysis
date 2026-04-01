const fs=require('fs');let c=fs.readFileSync('src/api/file.js'); c=c.filter(b => b !== 0); fs.writeFileSync('src/api/file.js', c); console.log('fixed');
