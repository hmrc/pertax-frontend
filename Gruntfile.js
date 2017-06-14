module.exports = function(grunt){
  grunt.initConfig({
    // Builds libsass
    sass: {
      dev: {
        options: {
          style: "expanded",
          sourcemap: "none"
        },
        files: [{
          expand: true,
          cwd: "app/assets/sass",
          src: ["*.scss"],
          dest: "public/stylesheets/",
          ext: ".css"
        }]
      }
    },
    //Checks styles
    scsslint: {
      allFiles: ['app/assets/sass/**/*.scss',],
      options: {
        bundleExec: false,
        colorizeOutput: true,
        reporterOutput: null
      }
    },
    //Checks javscript - not used yet
    coffeelint: {
      app: ['app/assets/javascripts/*.coffee'],
      options: {
        'no_trailing_whitespace': {
          'level': 'error'
        },
        force: true
      }
    },
    // Watches styles and specs for changes
    watch: {
      css: {
        files: ['app/assets/sass/**/*.scss'],
        tasks: ['sass'],
        options: { nospawn: true }
      },
      coffee: {
        files: ['app/assets/javascripts/*.coffee'],
        tasks: ['coffeelint']
      }
    }    
  });

  [
    'grunt-contrib-watch',
    'grunt-contrib-csslint',
    'grunt-coffeelint',
    'grunt-contrib-sass',
    'grunt-scss-lint'
  ].forEach(function (task) {
    grunt.loadNpmTasks(task);
  });

  grunt.registerTask('default', ['scsslint', 'sass', 'coffeelint']);
};