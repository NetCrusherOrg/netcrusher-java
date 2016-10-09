(function (global) {
    SystemJS
        .config({
            baseURL: './',
            defaultJSExtensions: true,
            transpiler: "none",

            paths: {
                'npm:': 'node_modules/'
            },

            map: {
                'js': 'js',
                'bootstrap': 'npm:bootstrap',
                'jquery': 'npm:jquery'
            },

            packages: {
                'jquery': {
                    main: 'dist/jquery.min.js',
                    defaultExtension: 'js'
                },
                'bootstrap': {
                    main: 'dist/js/bootstrap.min.js',
                    defaultExtension: 'js'
                }
            },

            meta: {
                'bootstrap/dist/js/bootstrap.min.js': {
                    format: 'global',
                    deps: ['jquery']
                },
                'jquery': {
                    format: 'global'
                }
            }
        });
})(this);