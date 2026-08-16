import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { User } from '../../core/models';
@Component({standalone:true,imports:[CommonModule,RouterLink],templateUrl:'./people.component.html',styleUrl:'./people.component.scss'})
export class PeopleComponent implements OnInit {people=signal<User[]>([]);constructor(public auth:AuthService,private api:CompanyApiService){}ngOnInit(){const request=this.auth.user()?.role==='ADMIN'?this.api.users():this.api.departmentMembers();request.subscribe(x=>this.people.set(x));}}
