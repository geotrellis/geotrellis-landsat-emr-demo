import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import App from '../containers/App';

var initialState = {
    rootUrl: "http://" + window.location.hostname + ":8899",

  map: {
    url: [],
    bounds: undefined
  },

  catalog: {
    layers : []
  }
};

var store =  require('../redux/store')(initialState);

render(
  <Provider store={store}>
    <App />
  </Provider>,
  document.body
);
