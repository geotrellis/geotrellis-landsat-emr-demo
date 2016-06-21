import React from 'react';
import { render } from 'react-dom';
import { Map, Marker, Popup, TileLayer, BaseTileLayer, GeoJson, FeatureGroup, Circle } from 'react-leaflet';
import { EditControl } from 'react-leaflet-draw';
import _ from 'lodash';
import "leaflet/dist/leaflet.css";
import 'leaflet-draw/dist/leaflet.draw.css';

var Leaflet = React.createClass({


  // Structure: /mean/{layer}/{zoom}/{ndwi/ndvi}?time=2015-06-29T00:00:00-0400'
  _onlayeradd: function(ev) {
    let fgroup = ev.target;
    let layer = ev.layer;

    let root = this.props.rootUrl;
    let zoom = layer._map._zoom;
    let t0 = this.props.times[0];
    let t1 = this.props.times[1];
    let layerName = this.props.layerName;
    let ndi = this.props.ndi;
    let that = this;

    geom.layer.props = {
      rootURL: this.props.rootUrl,
      zoom: geom.layer._map._zoom,
      t: this.props.times,
      layerName: this.props.layerName,
      ndi: this.props.ndi
    };
    geom.layer.on('click', function(l) { debugger; });


    let bindTimeSeriesData = function(marker) {
      var latlng = marker._latlng;
      var url = `${root}/series/${layerName}/${zoom}/${ndi}?lng=${latlng.lng}&lat=${latlng.lat}`;
      that.props.fetchTimeSeries(marker, url, ndi, latlng);
    };

    let bindPolygonalSummaryData = function(polygon) {
      if (that.props.layerType == 'singleLayer') {
        var url = `${root}/mean/${layerName}/${zoom}/${ndi}?time=${t0}`;
        that.props.fetchPolygonalSummary(layer, url, ndi);
      } else {
        var url = `${root}/mean/${layerName}/${zoom}/${ndi}?time=${t0}&otherTime=${t1}`;
        that.props.fetchPolygonalSummary(polygon, url, ndi + ' difference');
      }
    };

    // clean up our old geoms
    _.chain(fgroup.getLayers())
      .filter(function(l) { return  l._leaflet_id !== layer._leaflet_id; })
      .each(function(l) { fgroup.removeLayer(l); })
      .value();

    // Get our data
    if (layer._latlng) { // For marker/point specific logic
      layer.dragging.enable();
      layer.on('dragstart', function(ev) {
        layer.unbindPopup();
        layer.closePopup();
      });
      layer.on('dragend', function(ev) { bindTimeSeriesData(layer); });
      bindTimeSeriesData(layer);
    } else if (layer._latlngs) { // For polygonal summary (mean)
      layer.setStyle({ color: ndi == 'ndvi' ? '#64c59d' : '#0066ff' });
      bindPolygonalSummaryData(layer);
    }
  },

  _onDeleted: function(e) {
    console.log("Delete", e)
  },

  render: function() {
    const style = {
      minHeight: "800px", width: "100%"
    };
    let tileLayers = _.map(this.props.url, u => {
      return <TileLayer key={1} url={u} />;
    });

    let vectorLayers = [];
    if(this.props.maxState) {
      vectorLayers.push(<GeoJson data={this.props.maxState} />);
    }

    if(this.props.maxAverageState) {
      let state = this.props.maxAverageState;
      vectorLayers.push(
        <GeoJson data={state}
          fillOpacity={0.0}
          color="#333300"
          weight={3}>
          <Popup>
            <div>
              <p><b>Name:</b> {state.properties.name}</p>
              <p><b>Average Temperature:</b> {state.properties["Mean Temperature"]} °F</p>
            </div>
          </Popup>
        </GeoJson>
      );
    }

    if(this.props.stateAverage) {
      let state = this.props.stateAverage;
      vectorLayers.push(
        <GeoJson data={state}
          fillOpacity={0.0}
          color="#333300"
          weight={3}>
          <Popup>
            <div>
              <p><b>Name:</b> {state.properties.name}</p>
              <p><b>Average Temperature:</b> {parseFloat(Math.round(state.properties.meanTemp * 100) / 100).toFixed(2)} °F</p>
            </div>
          </Popup>
        </GeoJson>
      );
    }

    if(this.props.stateDiffAverage) {
      let state = this.props.stateDiffAverage;
      vectorLayers.push(
        <GeoJson data={state}
          fillOpacity={0.0}
          color="#333300"
          weight={3}>
          <Popup>
            <div>
              <p>Name: <b>{state.properties.name}</b></p>
              <p>Average Difference of Temperature: <b>{state.properties.meanTemp} °F</b></p>
            </div>
          </Popup>
        </GeoJson>
      );
    }

    var polyOptions = {
      stroke: true,
      weight: 3,
      color: '#C0C0C0',
      fillOpacity: 0.15,
      fillColor: null // falls back on stroke color
    }

    console.log(vectorLayers);

    return (
      <Map center ={[37.062, -121.530]} zoom={8} style={style} bounds={this.props.bounds}>
        <TileLayer url="http://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png"/>
        {tileLayers}
        {vectorLayers}
        <FeatureGroup onlayeradd={this._onlayeradd}>
          <EditControl
            position='topleft'
            onCreated={this._onDraw}
            onEdited={this._onDraw}
            onDeleted={this._onDeleted}
            draw={{
              line: false,
              polyline: false,
              circle: false,
              rectangle: false,
              polygon: { shapeOptions: polyOptions },
              marker: true
            }}
          />
        </FeatureGroup>
      </Map>
    );
  }
});

module.exports = Leaflet;
