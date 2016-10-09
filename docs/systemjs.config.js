(function (global) {
    SystemJS.config({
        baseURL: './',
        defaultJSExtensions: true,
        transpiler: "none",

        paths: {
            'npm:': 'node_modules/'
        },

        map: {
            'app': 'js',
            'bootstrap': 'npm:bootstrap',
            'jquery': 'npm:jquery',
            'highlight.js': 'npm:highlight.js',
            'highlight.languages': 'npm:highlight.js/lib/languages'
        },

        packages: {
            'jquery': {
                main: 'dist/jquery.min.js',
                defaultExtension: 'js'
            },
            'bootstrap': {
                main: 'dist/js/bootstrap.min.js',
                defaultExtension: 'js'
            },
            'highlight.js': {
                main: 'lib/highlight.js',
                defaultExtension: 'js'
            },
            'js': {
                main: 'app.js',
                defaultExtension: 'js'
            }
        },

        meta: {
            'bootstrap/*': {
                format: 'global',
                deps: ['jquery']
            },
            'jquery/*': {
                format: 'global'
            },
            'highlight.js/*': {
                format: 'cjs'
            },
            'js/*': {
                format: 'cjs',
                deps: ['jquery', 'bootstrap', 'highlight.js']
            }
        }
    });
})(this);