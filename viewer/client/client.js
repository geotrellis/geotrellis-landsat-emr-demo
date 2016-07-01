import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import App from '../containers/App';
import configureStore from '../redux/store.js'

var initialState = {
  rootUrl: "http://" + window.location.hostname + ":8899",
  layerName: undefined,
  analysisLayer: undefined,
  layerType: 'singleLayer',
  times: {}, // A map from layer to times selected for that layer
  ndi: 'ndvi',
  map: {
    url: [],
    bounds: undefined
  },
  catalog: {
    layers : []
  }
};

var store = configureStore(initialState);

render(
  <Provider store={store}>
    <App />
  </Provider>,
  document.body
);
