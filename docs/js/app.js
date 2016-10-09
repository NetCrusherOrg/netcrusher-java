var $ = require('jquery');

var hljs = require('highlight.js');
hljs.registerLanguage('java', require('highlight.langs/java'));
hljs.configure({useBR: false});
hljs.initHighlighting();

