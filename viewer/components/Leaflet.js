import React from 'react';
import { render } from 'react-dom';
import { Map, Marker, Popup, TileLayer, BaseTileLayer, GeoJson, FeatureGroup, Circle } from 'react-leaflet';
import { EditControl } from 'react-leaflet-draw';
import _ from 'lodash';
import $ from 'jquery';
import L from 'leaflet';
import "leaflet/dist/leaflet.css";
import 'leaflet-draw/dist/leaflet.draw.css';
import './leaflet-icons.css';


var Leaflet = React.createClass({

  _onlayeradd: function(ev) {
    var fgroup = ev.target;
    var addedLayer = ev.layer;
    var setAnalysisLayer = this.props.setAnalysisLayer;

    // Initialize object for holding chart-rendering parameters
    addedLayer.chartProps = {
      rootURL: this.props.rootUrl,
      times: this.props.times,
      layerName: this.props.layerName,
      comparisonType: this.props.layerType,
      geomType: (_.isUndefined(addedLayer._latlngs) ? 'point' : 'polygon'),
      selected: false
    };

    // Initialize object for different types of statistic
    addedLayer.stats = {};

    let setMarkerSelection = function(marker) {
      if (marker.chartProps.selected) {
        marker.setIcon(L.divIcon({className: 'selected-marker'}));
      } else {
        marker.setIcon(L.divIcon({className: 'unselected-marker'}));
      }
    };

    let setPolySelection = function(poly) {
      if (poly.chartProps.selected) {
        poly.setStyle({ color: '#ffff64' });
      } else {
        poly.setStyle({ color: '#64c59d' });
      }
    };

    let selectLayer = function() {
      let allLayers = fgroup.getLayers();

      // deselect all other layers
      _.chain(allLayers)
        .filter(function(l) { return l !== addedLayer; })
        .each(function(l) { l.chartProps.selected = false; })
        .value();

      addedLayer.chartProps.selected = true;
      _.each(allLayers, function(l) {
        if (l._latlng) {
          setMarkerSelection(l);
        } else {
          setPolySelection(l);
        }
      });

      setAnalysisLayer(addedLayer);
    };
    addedLayer.on('click', function(ev) { selectLayer(); });
    selectLayer();
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

    let polyOptions = {
      stroke: true,
      weight: 3,
      color: '#64c59d',
      fillOpacity: 0.15,
      fillColor: null // falls back on stroke color
    };

    let markerOptions = {
    };

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
              marker: { icon: L.divIcon({className: 'unselected-marker'}) }
            }}
          />
        </FeatureGroup>
      </Map>
    );
  }
});

module.exports = Leaflet;
