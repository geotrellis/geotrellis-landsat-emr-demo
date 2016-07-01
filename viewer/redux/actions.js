import fetch from 'isomorphic-fetch';
import shortid from 'shortid';
import $ from 'jquery';

var actions = {
  setLayerType: function(layerType) {
    return {
      type: 'SET_LAYER_TYPE',
      layer: layerType
    };
  },
  registerTime: function(time, index) {
    return {
      type: 'REGISTER_TIME',
      time: time,
      index: index
    };
  },
  setIndexType: function(ndi) {
    return {
      type: 'SET_NDI',
      ndi: ndi
    };
  },
  setLayerName: function(layerName) {
    return {
      type: 'SET_LAYERNAME',
      name: layerName
    };
  },
  showLayer: function (url) {
    return {
      type: 'SHOW_LAYER',
      url: url
    };
  },
  centerMap: function (extent) {
    return {
      type: 'CENTER_MAP',
      extent: extent
    };
  },
  showBounds: function(bounds) {
    return {
      type: 'SHOW_BOUNDS',
      bounds: bounds
    };
  },
  showExtent: function(extent) {
    return actions.showBounds([ [extent[0][1], extent[0][0]], [extent[1][1], extent[1][0]] ]);
  },
  loadCatalogRequest: function(url) {
    return {
      type: 'LOAD_CATALOG_REQEST',
      url: url
    };
  },
  loadCatalogSuccess: function(url, catalog) {
    return {
      type: 'LOAD_CATALOG_SUCCESS',
      url: url,
      catalog: catalog
    };
  },
  loadCatalogFailure: function(url, error) {
    return {
      type: 'LOAD_CATALOG_ERROR',
      url: url,
      error: error
    };
  },
  setAnalysisLayer: function(layer) {
    return {
      type: 'SET_ANALYSIS_LAYER',
      layer: layer
    };
  },
  fetchCatalog: function (url) {
    return dispatch => {
      dispatch(actions.loadCatalogRequest(url));
      console.log("FETCH CATALOG", url + "/catalog");
      return fetch(url + "/catalog").then( response => {
        response.json().then( json => {
          dispatch(actions.loadCatalogSuccess(url, json));
        });
      },
      error => dispatch(actions.loadCatalogFailure(url, error)));
    };
  },
  fetchPolygonalSummary: function(polygonLayer) {
    // type should be NDVI or NDWI
    // answer should be the computed mean value
    let singlePolySummaryTemplate = _.template("<h4>Average <%- type %>: <%- answer %></h4>");

    return dispatch => {
      console.log("Fetching polygonal summary", polygonLayer.toGeoJSON().geometry);
      //polygonLayer.bindPopup('<h4>Loading...</h4>');
      return fetch(url, {
        method: 'POST',
        body: JSON.stringify(polygonLayer.toGeoJSON().geometry)
      }).then( response => {
        response.json().then( summary => {
          summary.type = indexType;
          if (_.isNull(summary.answer)) {
            polygon.stats[polygonLayer.chartProps.ndi] = null;
          } else {
            polygon.stats[polygonLayer.chartProps.ndi] = summary.answer.toFixed(4);
          }
        });
      },
      error => {
      });
    };
  },
  showLayerWithBreaks: function(layerUrl, breaksUrl, layerId) {
    return dispatch => {
      console.log("Fetching breaks", breaksUrl);
      return fetch(breaksUrl).then( response => {
        response.json().then( breaks => {
          dispatch(actions.showLayer(layerUrl + "&breaks=" + breaks.join(","), layerId));
        });
      },
      error => {});
    };
  },
  showMaxState: function(url) {
    return dispatch => {
      console.log("Fetching max state", url);
      return fetch(url)
        .then(
          response => {
            response.json().then( geojson => {
              dispatch({
                type: 'SHOW_MAX_STATE',
                geojson: geojson
              });
            });
          },
          error => {}
        );
      };
  },
  hideMaxState: function() {
    return {
      type: 'HIDE_MAX_STATE'
    };
  },
  showMaxAverageState: function(url) {
    return dispatch => {
      console.log("Fetching max average state", url);
      return fetch(url)
        .then(
          response => {
            response.json().then( geojson => {
              dispatch({
                type: 'SHOW_MAX_AVERAGE_STATE',
                geojson: geojson
              });
            });
          },
          error => {}
        );
      };
  },
  hideMaxAverageState: function() {
    return {
      type: 'HIDE_MAX_AVERAGE_STATE'
    };
  },
  showStateAverage: function(url) {
    return dispatch => {
      console.log("Fetching state average", url);
      return fetch(url)
        .then(
          response => {
            response.json().then( geojson => {
              dispatch({
                type: 'SHOW_STATE_AVERAGE',
                geojson: geojson
              });
            });
          },
          error => {}
        );
      };
  },
  showStateDiffAverage: function(url) {
    return dispatch => {
      console.log("Fetching state average", url);
      return fetch(url)
        .then(
          response => {
            response.json().then( geojson => {
              dispatch({
                type: 'SHOW_STATE_DIFF_AVERAGE',
                geojson: geojson
              });
            });
          },
          error => {}
        );
      };
  }
};

module.exports = actions;
