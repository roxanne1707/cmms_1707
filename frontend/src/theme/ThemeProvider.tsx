import React, { useEffect, useMemo, useState } from 'react';
import { createTheme, ThemeProvider } from '@mui/material';
import { zhCN } from '@mui/material/locale';
import { themeCreator } from './base';
import { StylesProvider } from '@mui/styles';
import { CacheProvider } from '@emotion/react';
import createCache from '@emotion/cache';
import stylisRTLPlugin from 'stylis-plugin-rtl';
import { useTranslation } from 'react-i18next';

const isPrerender =
  typeof navigator !== 'undefined' &&
  navigator.userAgent.toLowerCase().indexOf('prerender') !== -1;

const cacheRtl = createCache({
  key: 'bloom-ui-rtl',
  prepend: true,
  speedy: !isPrerender,
  // @ts-ignore
  stylisPlugins: [stylisRTLPlugin]
});

const cacheLtr = createCache({
  key: 'bloom-ui-ltr',
  prepend: true,
  speedy: !isPrerender
});

export const ThemeContext = React.createContext(
  (themeName: string): void => {}
);

const ThemeProviderWrapper: React.FC = (props) => {
  const curThemeName = localStorage.getItem('appTheme') || 'PureLightTheme';
  const [themeName, _setThemeName] = useState(curThemeName);
  const { i18n } = useTranslation();
  const chinese = i18n.language?.startsWith('zh');
  const theme = useMemo(
    () => createTheme(themeCreator(themeName), chinese ? zhCN : {}, chinese ? {
      components: Object.fromEntries(
        ['MuiDatePicker', 'MuiDesktopDatePicker', 'MuiMobileDatePicker',
          'MuiDateTimePicker', 'MuiDesktopDateTimePicker', 'MuiMobileDateTimePicker',
          'MuiTimePicker', 'MuiDesktopTimePicker', 'MuiMobileTimePicker'].map(name => [name, {
            defaultProps: {
              cancelText: '取消', okText: '确定', clearText: '清除', todayText: '今天',
              toolbarTitle: name.includes('DateTime') ? '选择日期与时间' : name.includes('Time') ? '选择时间' : '选择日期'
            }
          }])
      )
    } : {}),
    [themeName, chinese]
  );
  const rtl = i18n.dir() === 'rtl';
  const setThemeName = (themeName: string): void => {
    localStorage.setItem('appTheme', themeName);
    _setThemeName(themeName);
  };

  useEffect(() => {
    if (rtl) document.documentElement.setAttribute('dir', 'rtl');
    else document.documentElement.removeAttribute('dir');
  }, [rtl]);

  const providers = (
    <ThemeContext.Provider value={setThemeName}>
      <ThemeProvider theme={theme}>{props.children}</ThemeProvider>
    </ThemeContext.Provider>
  );
  return (
    <StylesProvider injectFirst>
      <CacheProvider value={rtl ? cacheRtl : cacheLtr}>
        {providers}
      </CacheProvider>
    </StylesProvider>
  );
};

export default ThemeProviderWrapper;
