var $ = require('jquery');

var hljs = require('highlight.js');
hljs.registerLanguage('java', require('highlight.languages/java'));
hljs.configure({useBR: false});
hljs.initHighlighting();

