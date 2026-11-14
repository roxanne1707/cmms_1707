// config-overrides.js
const { codeInspectorPlugin } = require('code-inspector-plugin');

module.exports = {
  webpack: function override(config, env) {
    if (env === 'development') {
      config.plugins.push(
        codeInspectorPlugin({
          bundler: 'webpack',
          editor: 'idea',
          hotKeys: ['altKey']
        })
      );
    }
    // Optional laptop/CI budget; keeps type checking and minification enabled.
    if (process.env.CMMS_BUILD_WORKERS) {
      const workers = Number(process.env.CMMS_BUILD_WORKERS);
      if (!Number.isInteger(workers) || workers < 1 || workers > 8) {
        throw new Error('CMMS_BUILD_WORKERS must be an integer from 1 to 8');
      }
      for (const minimizer of config.optimization?.minimizer || []) {
        if (minimizer?.options && 'parallel' in minimizer.options) minimizer.options.parallel = workers;
      }
      for (const plugin of config.plugins) {
        if (plugin.constructor.name === 'ForkTsCheckerWebpackPlugin') {
          plugin.options.typescript = { ...plugin.options.typescript, memoryLimit: 768 };
        }
      }
    }
    if (env === 'production' && process.env.SENTRY_AUTH_TOKEN) {
      const { sentryWebpackPlugin } = require('@sentry/webpack-plugin');
      config.plugins.push(
        sentryWebpackPlugin({
          org: process.env.SENTRY_ORG,
          project: 'frontend',
          authToken: process.env.SENTRY_AUTH_TOKEN,
          release: {
            name:
              process.env.REACT_APP_SENTRY_RELEASE || 'atlas-frontend',
            create: true
          }
        })
      );
    }
    return config;
  },

  devServer: function (configFunction) {
    return function (proxy, allowedHost) {
      const config = configFunction(proxy, allowedHost);
      config.client = {
        ...config.client,
        overlay: false
      };

      return config;
    };
  }
};
