import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import App from '../containers/App';

var initialState = {
  rootUrl: "http://localhost:8088",

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
  document.getElementById('app')
);
