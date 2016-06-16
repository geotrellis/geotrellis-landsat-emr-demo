import React from 'react';
import { render } from 'react-dom';
import { Map, Marker, Popup, TileLayer, BaseTileLayer, GeoJson, FeatureGroup, Circle } from 'react-leaflet';
import { EditControl } from 'react-leaflet-draw';
import _ from 'lodash';
import "leaflet/dist/leaflet.css";
import 'leaflet-draw/dist/leaflet.draw.css';

var Leaflet = React.createClass({

  // Structure: /mean/{layer}/{zoom}/{ndwi/ndvi}?time=2015-06-29T00:00:00-0400'
  _onDraw: function(geom) {
    let root = this.props.rootUrl;
    let zoom = geom.layer._map._zoom;
    let t0 = this.props.times[0];
    let t1 = this.props.times[1];
    let layerName = this.props.layerName;
    let ndi = this.props.ndi;


    if (geom.layerType == 'marker') { // For point timeseries
      var latlng = geom.layer._latlng;
      var url = `${root}/series/${layerName}/${zoom}/${ndi}?lng=${latlng.lng}&lat=${latlng.lat}`;
      this.props.fetchTimeSeries(geom.layer, url);
    } else { // For polygonal summary (mean)
      if (this.props.layerType == 'singleLayer') {
        var url = `${root}/mean/${layerName}/${zoom}/${ndi}?time=${t0}`;
        this.props.fetchPolygonalSummary(geom.layer, url, ndi);
      } else {
        var url = `${root}/mean/${layerName}/${zoom}/${ndi}?time=${t0}&otherTime=${t1}`;
        this.props.fetchPolygonalSummary(geom.layer, url, ndi + ' difference');
      }
    }
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

    console.log(vectorLayers);

    return (
      <Map center ={[37.062, -121.530]} zoom={8} style={style} bounds={this.props.bounds}>
        <TileLayer url="http://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png"/>
        {tileLayers}
        {vectorLayers}
        <FeatureGroup>
          <EditControl
            position='topleft'
            onCreated={this._onDraw}
            onEdited={this._onDraw}
            draw={{
              line: false,
              polyline: false,
              rectangle: true,
              circle: false,
              polygon: true,
              marker: true
            }}
          />
        </FeatureGroup>
      </Map>
    );
  }
});

module.exports = Leaflet;
