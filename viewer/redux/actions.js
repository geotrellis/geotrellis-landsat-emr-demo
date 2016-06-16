import fetch from 'isomorphic-fetch';
import shortid from 'shortid';
import timeSeries from '../charts/timeSeries.js'

var actions = {
  setTime: function(time, whichTime) {
    console.log(time, whichTime, 'zzzzz');
    var actionType;
    if (whichTime == 1) {
      actionType = 'SET_T1';
    } else if (whichTime == 2) {
      actionType = 'SET_T2';
    }
    return {
      type: actionType,
      time: time
    };
  },
  setIndexType: function(ndi) {
    return {
      type: 'SET_NDI',
      ndi: ndi
    };
  },
  setLayerName: function(layer) {
    var name;
    if (layer) { name = layer.name; }
    return {
      type: 'SET_LAYERNAME',
      name: name
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
  fetchPolygonalSummary: function(polygonLayer, url) {
    // type should be NDVI or NDWI
    // answer should be the computed mean value
    let singlePolySummaryTemplate = _.template("Average <%- type %>: <%- answer %>");

    return dispatch => {
      console.log("Fetching polygonal summary", polygonLayer.toGeoJSON());
      window.p = polygonLayer;
      return fetch("/", {
        method: 'POST'
      }).then( response => {
        var dummy = { "answer": -0.2394847059657793 };
        dummy.type = "NDVI";
        polygonLayer.bindPopup(singlePolySummaryTemplate(dummy));
        //response.json().then( summary => {
        //});
      },
      error => {
      });
    };
  },
  fetchTimeSeries: function(pointLayer, url) {
    return dispatch => {
      console.log("Fetching timeseries data", pointLayer.toGeoJSON());
      var chartID = shortid.generate();
      return fetch("/").then( response => {
        var dummy = { "answer": [["2015-06-29T04:00:00.000Z", -0.23400004343388278], ["2015-06-20T04:00:00.000Z", -0.054853387259858444]] };
        var data = _.map(dummy.answer, function(d) { console.log("a d", d);return { "date": new Date(d[0]), "value": d[1] }; });
        pointLayer.on("click", function() {
          pointLayer.bindPopup('<div id="' + chartID + '" style="width: 400px; height: 200px;"><svg/></div>',
                               { 'maxWidth': 450 });
          pointLayer.openPopup();
          timeSeries(chartID, data);

          // Evidently unbinding is necessary for the graph to be rerendered properly
          pointLayer.unbindPopup();
        });

        //response.json().then( summary => {
        //});
      },
      error => {
      });
    }
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
